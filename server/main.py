import os
import io
import json
import base64
import time
import uuid
import cv2
import numpy as np
from datetime import datetime
from typing import List, Optional
from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from ultralytics import YOLO
from PIL import Image, ImageOps

# 引入自訂資料庫模組
import database

# ==============================================================================
# 1. FastAPI 應用程式初始化與跨域設定
# ==============================================================================
app = FastAPI(
    title="CarGuard AI Detection & Database API",
    description="整合雙階段 YOLO 車損分割與 7 大核心資料庫（車輛、會員、訂單、照片、AI標註、客服審核、OBD）",
    version="3.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
STATIC_DIR = os.path.join(BASE_DIR, "static", "uploads")
os.makedirs(STATIC_DIR, exist_ok=True)
app.mount("/static", StaticFiles(directory=os.path.join(BASE_DIR, "static")), name="static")

# ==============================================================================
# 2. 雙階段車損偵測核心 (基於 NewYoloModel/example.py 演算法)
# ==============================================================================
class TwoStageCarDamageDetector:
    def __init__(self, stage1_model="yolov8n-seg.pt", stage2_model="NewYoloModel/best.pt", car_class_ids=(2, 5, 7)):
        stage2_path = os.path.join(BASE_DIR, stage2_model)
        if not os.path.exists(stage2_path):
            stage2_path = os.path.join(BASE_DIR, "NewYoloModel", "best.pt")
            
        print(f"🔄 載入 Stage 1 模型: {stage1_model}...")
        self.m1 = YOLO(stage1_model)
        print(f"🔄 載入 Stage 2 車損模型: {stage2_path}...")
        self.m2 = YOLO(stage2_path)
        self.car_ids = car_class_ids
        print("✅ 車損雙階段模型載入完成！")

    def get_car_mask(self, img):
        h, w = img.shape[:2]
        car_mask = np.zeros((h, w), dtype=np.uint8)
        res = self.m1.predict(source=img, conf=0.35, verbose=False)[0]

        if res.masks is not None:
            for box, mask_data in zip(res.boxes, res.masks.data):
                if int(box.cls[0]) in self.car_ids:
                    mask = cv2.resize(
                        mask_data.cpu().numpy().astype(np.uint8),
                        (w, h),
                        interpolation=cv2.INTER_NEAREST,
                    )
                    car_mask = cv2.bitwise_or(car_mask, mask)
        
        if np.count_nonzero(car_mask) < (h * w * 0.05):
            car_mask = np.ones((h, w), dtype=np.uint8) * 255

        return car_mask

    def check_canny_density(self, roi, low_thresh=30, high_thresh=120, density_thresh=0.015):
        if roi.size == 0:
            return False
        gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
        edges = cv2.Canny(cv2.GaussianBlur(gray, (5, 5), 0), low_thresh, high_thresh)
        return (np.count_nonzero(edges) / float(edges.size)) >= density_thresh

    def detect_and_annotate(self, img, damage_conf=0.18, overlap_thresh=0.20, canny_thresh=0.02):
        h, w = img.shape[:2]
        annotated = img.copy()
        overlay = img.copy()

        car_mask = self.get_car_mask(img)
        damage_res = self.m2.predict(source=img, conf=damage_conf, verbose=False)[0]

        predictions = []

        if damage_res.boxes is not None:
            for i, box in enumerate(damage_res.boxes):
                x1, y1, x2, y2 = map(int, box.xyxy[0].tolist())
                x1, y1, x2, y2 = max(0, x1), max(0, y1), min(w, x2), min(h, y2)
                box_area = (x2 - x1) * (y2 - y1)

                roi_mask = np.zeros((h, w), dtype=np.uint8)
                roi_mask[y1:y2, x1:x2] = 1
                overlap = cv2.bitwise_and(roi_mask, roi_mask, mask=car_mask)
                if (np.count_nonzero(overlap) / float(box_area if box_area > 0 else 1)) < overlap_thresh:
                    continue

                if not self.check_canny_density(img[y1:y2, x1:x2], density_thresh=canny_thresh):
                    continue

                if damage_res.masks is not None and i < len(damage_res.masks.data):
                    m = cv2.resize(
                        damage_res.masks.data[i].cpu().numpy().astype(np.uint8),
                        (w, h),
                        interpolation=cv2.INTER_NEAREST,
                    )
                    overlay[m == 1] = (255, 0, 150)

                cls_name = self.m2.names[int(box.cls[0])]
                conf = float(box.conf[0])

                cv2.rectangle(annotated, (x1, y1), (x2, y2), (0, 0, 255), 2)
                cv2.putText(
                    annotated,
                    f"{cls_name} {conf:.2f}",
                    (x1, max(y1 - 10, 20)),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.6,
                    (0, 0, 255),
                    2,
                )

                predictions.append({
                    "label": cls_name,
                    "confidence": round(conf, 4),
                    "box": [float(x1), float(y1), float(x2), float(y2)],
                    "pos": f"X:{x1}~{x2}, Y:{y1}~{y2}"
                })

        cv2.addWeighted(overlay, 0.4, annotated, 0.6, 0, annotated)
        return annotated, predictions

detector = TwoStageCarDamageDetector()

# ==============================================================================
# 3. 圖片品質檢測工具
# ==============================================================================
def inspect_image_quality(img):
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    blur_score = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    mean_brightness = float(np.mean(gray))
    return {
        "blur_score": round(blur_score, 2),
        "mean_brightness": round(mean_brightness, 2)
    }

# ==============================================================================
# 4. API 路由端點 (連接 database.py)
# ==============================================================================
@app.get("/")
def root():
    return {
        "service": "CarGuard AI Detection & Database API",
        "database": "SQLite (Schema matching PRD Specification)",
        "status": "online",
        "docs": "/docs"
    }

@app.post("/predict")
async def predict_damage(
    file: UploadFile = File(...),
    plate_number: Optional[str] = Form("RFH-8052"),
    user_id: Optional[str] = Form("U0882"),
    angle: Optional[str] = Form("front_left")
):
    """
    接收 Android App 上傳之照片，執行品質檢測與 YOLO 車損辨識，並將完整的訂單/照片/瑕疵紀錄寫入資料庫
    """
    try:
        file_bytes = await file.read()
        try:
            pil_img = Image.open(io.BytesIO(file_bytes))
            pil_img = ImageOps.exif_transpose(pil_img)
            img = cv2.cvtColor(np.array(pil_img), cv2.COLOR_RGB2BGR)
        except Exception:
            nparr = np.frombuffer(file_bytes, np.uint8)
            img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        if img is None:
            raise HTTPException(status_code=400, detail="無法解碼上傳之圖片檔案")

        # 1. 影像品質檢測
        quality_metrics = inspect_image_quality(img)

        # 2. 執行最新雙階段 YOLO 車損辨識
        annotated_img, predictions = detector.detect_and_annotate(img)
        damage_count = len(predictions)

        # 3. 儲存檔案與生成 Base64
        order_id = f"ORD-{datetime.now().strftime('%Y%m%d')}-{uuid.uuid4().hex[:4].upper()}"
        orig_filename = f"{order_id}_orig.jpg"
        anno_filename = f"{order_id}_anno.jpg"

        orig_path = os.path.join(STATIC_DIR, orig_filename)
        anno_path = os.path.join(STATIC_DIR, anno_filename)

        cv2.imwrite(orig_path, img)
        cv2.imwrite(anno_path, annotated_img)

        _, buffer = cv2.imencode(".jpg", annotated_img, [cv2.IMWRITE_JPEG_QUALITY, 85])
        annotated_base64 = base64.b64encode(buffer).decode("utf-8")

        # 4. 寫入資料庫 (Order, VehiclePhoto, AIDetection 三張表)
        angle_key = angle.lower().replace("-", "_")
        if angle_key not in ["front_left", "front_right", "rear_left", "rear_right", "front_inside", "rear_inside"]:
            angle_key = "front_left"

        database.save_inspection_result(
            order_id=order_id,
            plate_number=plate_number,
            user_id=user_id,
            angle=angle_key,
            raw_url=f"http://localhost:8000/static/uploads/{orig_filename}",
            annotated_url=f"http://localhost:8000/static/uploads/{anno_filename}",
            quality_metrics=quality_metrics,
            predictions=predictions
        )

        return {
            "status": "success",
            "message": f"辨識完成，偵測到 {damage_count} 處疑似車損",
            "order_id": order_id,
            "damage_count": damage_count,
            "quality_metrics": quality_metrics,
            "predictions": predictions,
            "annotated_image_base64": f"data:image/jpeg;base64,{annotated_base64}"
        }

    except Exception as e:
        return {
            "status": "error",
            "message": f"推論過程發生錯誤: {str(e)}",
            "damage_count": 0,
            "predictions": []
        }

@app.get("/api/cases")
def list_cases(status: Optional[str] = None):
    """
    提供給 Streamlit 客服管理後台獲取所有資料庫中的訂單記錄與比對資訊
    """
    cases = database.get_all_orders_for_admin()
    if status and status != "all":
        return [c for c in cases if c.get("status") == status]
    return cases

@app.patch("/api/cases/{order_id}/review")
def review_case(order_id: str, payload: dict):
    """
    客服人員審核案件 API (同步更新資料庫 orders 與 human_reviews 表)
    """
    status = payload.get("status", "confirmed")
    note = payload.get("note", "")
    reviewer_name = payload.get("reviewer", "客服專員 08")
    penalty_amount = payload.get("penalty_amount", 0)

    database.update_human_review(
        order_id=order_id,
        status=status,
        note=note,
        reviewer_name=reviewer_name,
        penalty_amount=penalty_amount
    )
    return {"status": "success", "order_id": order_id}

@app.get("/api/vehicles")
def list_vehicles():
    """
    獲取所有車輛即時營運狀態與歷史車損帳本 (Damage Ledger)
    """
    return database.get_all_vehicles_with_ledger()
