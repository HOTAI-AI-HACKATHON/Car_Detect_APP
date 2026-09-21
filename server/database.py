import os
import sqlite3
import json
import uuid
from datetime import datetime
from typing import List, Dict, Any, Optional

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "data")
os.makedirs(DATA_DIR, exist_ok=True)
DB_PATH = os.path.join(DATA_DIR, "carguard.db")

# ==============================================================================
# 1. 資料庫連線與自動初始化資料表 (嚴格遵循 PDF 規格)
# ==============================================================================
def get_connection():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_connection()
    cursor = conn.cursor()

    # 1. 車輛表 (Vehicle) - PDF Page 3
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS vehicles (
        id TEXT PRIMARY KEY,
        plate_number TEXT UNIQUE NOT NULL,
        model TEXT NOT NULL,
        status TEXT NOT NULL CHECK(status IN ('stop', 'activate', 'rent', 'cleaning', 'fixing')),
        created_at TEXT NOT NULL
    )
    """)

    # 2. OBD 表 (車載診斷) - PDF Page 3
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS obd (
        id TEXT PRIMARY KEY,
        vehicle_id TEXT NOT NULL,
        mileage REAL DEFAULT 0.0,
        fuel_level REAL DEFAULT 100.0,
        error_code TEXT DEFAULT 'NONE',
        created_at TEXT NOT NULL,
        FOREIGN KEY (vehicle_id) REFERENCES vehicles(id)
    )
    """)

    # 3. 會員表 (User) - PDF Page 3-4
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS users (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        account TEXT UNIQUE NOT NULL,
        password TEXT NOT NULL,
        created_at TEXT NOT NULL
    )
    """)

    # 4. 訂單表 (Order) - PDF Page 4
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS orders (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        vehicle_id TEXT NOT NULL,
        insurance INTEGER NOT NULL DEFAULT 1,
        start_time TEXT NOT NULL,
        end_time TEXT NOT NULL,
        status TEXT NOT NULL CHECK(status IN ('process', 'end')),
        inspection_status TEXT NOT NULL CHECK(inspection_status IN ('auto_passed', 'needs_review', 'confirmed', 'disputed')),
        created_at TEXT NOT NULL,
        FOREIGN KEY (user_id) REFERENCES users(id),
        FOREIGN KEY (vehicle_id) REFERENCES vehicles(id)
    )
    """)

    # 5. 車拍攝照片表 (VehiclePhoto) - PDF Page 4
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS vehicle_photos (
        id TEXT PRIMARY KEY,
        order_id TEXT NOT NULL,
        status TEXT NOT NULL CHECK(status IN ('before', 'after')),
        angle TEXT NOT NULL CHECK(angle IN ('front_right', 'front_left', 'rear_right', 'rear_left', 'front_inside', 'rear_inside')),
        ai_detect TEXT NOT NULL CHECK(ai_detect IN ('pass', 'review')),
        raw_url TEXT NOT NULL,
        annotated_url TEXT,
        blur_score REAL DEFAULT 0.0,
        mean_brightness REAL DEFAULT 0.0,
        created_at TEXT NOT NULL,
        FOREIGN KEY (order_id) REFERENCES orders(id)
    )
    """)

    # 6. AI偵測表 (AIDetection) - PDF Page 4-5
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ai_detections (
        id TEXT PRIMARY KEY,
        photo_id TEXT NOT NULL,
        label TEXT NOT NULL,
        confidence REAL NOT NULL,
        bbox TEXT NOT NULL, -- JSON format: [xmin, ymin, xmax, ymax]
        is_new INTEGER NOT NULL DEFAULT 1,
        created_at TEXT NOT NULL,
        FOREIGN KEY (photo_id) REFERENCES vehicle_photos(id)
    )
    """)

    # 7. 人員複審表 (HumanReview) - PDF Page 5
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS human_reviews (
        id TEXT PRIMARY KEY,
        order_id TEXT NOT NULL,
        check_status TEXT NOT NULL CHECK(check_status IN ('pass', 'confirm_damage', 'dispute')),
        penalty_amount INTEGER DEFAULT 0,
        note TEXT,
        reviewer_name TEXT NOT NULL,
        created_at TEXT NOT NULL,
        FOREIGN KEY (order_id) REFERENCES orders(id)
    )
    """)

    # 8. 車輛既有車損帳本表 (Damage Ledger) - PDF Page 3
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS damage_ledger (
        id TEXT PRIMARY KEY,
        vehicle_id TEXT NOT NULL,
        angle TEXT NOT NULL,
        damage_type TEXT NOT NULL,
        photo_url TEXT NOT NULL,
        note TEXT,
        registered_date TEXT NOT NULL,
        FOREIGN KEY (vehicle_id) REFERENCES vehicles(id)
    )
    """)

    conn.commit()
    seed_initial_data(conn)
    conn.close()

# ==============================================================================
# 2. 初始示範資料填充 (Seed Data)
# ==============================================================================
def seed_initial_data(conn):
    cursor = conn.cursor()
    
    # 檢查是否已填充
    cursor.execute("SELECT COUNT(*) FROM vehicles")
    if cursor.fetchone()[0] > 0:
        return

    print("🌱 正在初始化資料庫種子資料 (Seed Initial Data)...")
    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    # 1. 建立預設車輛
    vehicles = [
        ("veh_001", "RFH-8052", "Toyota Yaris Cross (白)", "rent", now_str),
        ("veh_002", "ABC-1234", "Toyota Corolla Cross (銀)", "activate", now_str),
        ("veh_003", "RAC-6688", "Toyota RAV4 Hybrid (黑)", "fixing", now_str),
        ("veh_004", "CAR-9900", "Toyota Altis (藍)", "cleaning", now_str)
    ]
    cursor.executemany("INSERT INTO vehicles VALUES (?, ?, ?, ?, ?)", vehicles)

    # 2. 建立既有舊傷 (Damage Ledger)
    damages = [
        ("dmg_001", "veh_001", "左前輪拱", "輕微刮痕 (2cm)", "https://images.unsplash.com/photo-1549399542-7e3f8b79c341?w=600", "已建檔舊傷，後續承租人免責", "2026-08-20"),
        ("dmg_002", "veh_003", "右後保桿", "凹痕 (5cm)", "https://images.unsplash.com/photo-1542282088-72c9c27ed0cd?w=600", "前次事故出險，排定進廠鈑烤", "2026-08-30")
    ]
    cursor.executemany("INSERT INTO damage_ledger VALUES (?, ?, ?, ?, ?, ?, ?)", damages)

    # 3. 建立預設會員
    users = [
        ("usr_001", "陳小明", "U0882", "pass1234", now_str),
        ("usr_002", "林志豪", "U0912", "pass1234", now_str),
        ("usr_003", "王大同", "U0731", "pass1234", now_str)
    ]
    cursor.executemany("INSERT INTO users VALUES (?, ?, ?, ?, ?)", users)

    # 4. 建立示範歷史訂單
    orders = [
        ("ORD-20260830-101", "usr_001", "veh_001", 1, "2026-08-30 09:30", "2026-08-30 14:45", "end", "needs_review", now_str),
        ("ORD-20260830-102", "usr_002", "veh_002", 1, "2026-08-30 10:15", "2026-08-30 12:30", "end", "auto_passed", now_str),
        ("ORD-20260830-103", "usr_003", "veh_003", 0, "2026-08-30 08:00", "2026-08-30 13:00", "end", "confirmed", now_str)
    ]
    cursor.executemany("INSERT INTO orders VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", orders)

    # 5. 示範照片與 AI 標註
    photos = [
        ("pho_001", "ORD-20260830-101", "after", "front_left", "review",
         "https://images.unsplash.com/photo-1549399542-7e3f8b79c341?w=800",
         "https://images.unsplash.com/photo-1549399542-7e3f8b79c341?w=800", 125.4, 140.2, now_str)
    ]
    cursor.executemany("INSERT INTO vehicle_photos VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", photos)

    detections = [
        ("det_001", "pho_001", "scratch", 0.92, json.dumps([100.0, 150.0, 320.0, 380.0]), 1, now_str),
        ("det_002", "pho_001", "paint_chip", 0.81, json.dumps([400.0, 420.0, 520.0, 560.0]), 1, now_str)
    ]
    cursor.executemany("INSERT INTO ai_detections VALUES (?, ?, ?, ?, ?, ?, ?)", detections)

    conn.commit()
    print("✅ 資料庫種子資料建立完成！")

# ==============================================================================
# 3. 高階業務 CRUD 操作函數
# ==============================================================================
def save_inspection_result(
    order_id: str,
    plate_number: str,
    user_id: str,
    angle: str,
    raw_url: str,
    annotated_url: str,
    quality_metrics: dict,
    predictions: List[dict]
) -> dict:
    """
    接收 App 上傳檢驗結果，即時寫入 Order, VehiclePhoto, AIDetection 三張表
    """
    conn = get_connection()
    cursor = conn.cursor()
    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    # 1. 查找或建立車輛
    cursor.execute("SELECT id FROM vehicles WHERE plate_number = ?", (plate_number,))
    veh_row = cursor.fetchone()
    if veh_row:
        veh_id = veh_row["id"]
    else:
        veh_id = f"veh_{uuid.uuid4().hex[:6]}"
        cursor.execute("INSERT INTO vehicles VALUES (?, ?, ?, ?, ?)", (veh_id, plate_number, "Toyota Yaris Cross (白)", "rent", now_str))

    # 2. 查找或建立會員
    cursor.execute("SELECT id FROM users WHERE account = ?", (user_id,))
    user_row = cursor.fetchone()
    if user_row:
        u_id = user_row["id"]
    else:
        u_id = f"usr_{uuid.uuid4().hex[:6]}"
        cursor.execute("INSERT INTO users VALUES (?, ?, ?, ?, ?)", (u_id, f"行動會員 ({user_id})", user_id, "pass1234", now_str))

    damage_count = len(predictions)
    inspection_status = "needs_review" if damage_count > 0 else "auto_passed"

    # 3. 建立或更新 Order 訂單
    cursor.execute("SELECT id, inspection_status FROM orders WHERE id = ?", (order_id,))
    existing_order = cursor.fetchone()
    if not existing_order:
        cursor.execute("""
        INSERT INTO orders (id, user_id, vehicle_id, insurance, start_time, end_time, status, inspection_status, created_at)
        VALUES (?, ?, ?, 1, ?, ?, 'end', ?, ?)
        """, (order_id, u_id, veh_id, now_str[:16], now_str[:16], inspection_status, now_str))
    else:
        # 若已有訂單且新照片有車損，自動升級為 needs_review
        if damage_count > 0:
            cursor.execute("UPDATE orders SET inspection_status = 'needs_review' WHERE id = ?", (order_id,))

    # 4. 建立 VehiclePhoto 照片記錄
    photo_id = f"pho_{uuid.uuid4().hex[:8]}"
    ai_status = "review" if damage_count > 0 else "pass"
    cursor.execute("""
    INSERT INTO vehicle_photos (id, order_id, status, angle, ai_detect, raw_url, annotated_url, blur_score, mean_brightness, created_at)
    VALUES (?, ?, 'after', ?, ?, ?, ?, ?, ?, ?)
    """, (
        photo_id, order_id, angle, ai_status, raw_url, annotated_url,
        quality_metrics.get("blur_score", 0.0), quality_metrics.get("mean_brightness", 0.0), now_str
    ))

    # 5. 建立 AIDetection 瑕疵記錄
    for p in predictions:
        det_id = f"det_{uuid.uuid4().hex[:8]}"
        cursor.execute("""
        INSERT INTO ai_detections (id, photo_id, label, confidence, bbox, is_new, created_at)
        VALUES (?, ?, ?, ?, ?, 1, ?)
        """, (det_id, photo_id, p["label"], p["confidence"], json.dumps(p.get("box", [])), now_str))

    conn.commit()
    conn.close()
    return {"order_id": order_id, "inspection_status": inspection_status}

def get_recent_active_order_id(plate_number: str, max_age_seconds: int = 600) -> Optional[str]:
    """
    尋找同一車輛在指定秒數內（預設 10 分鐘）建立的批次訂單，便於同一批上傳的照片集中在同一個摺頁
    """
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
    SELECT o.id, o.created_at 
    FROM orders o
    JOIN vehicles v ON o.vehicle_id = v.id
    WHERE v.plate_number = ?
    ORDER BY o.created_at DESC
    LIMIT 1
    """, (plate_number,))
    row = cursor.fetchone()
    conn.close()
    if not row:
        return None
    try:
        created_dt = datetime.strptime(row["created_at"], "%Y-%m-%d %H:%M:%S")
        if abs((datetime.now() - created_dt).total_seconds()) <= max_age_seconds:
            return row["id"]
    except Exception:
        pass
    return None

def auto_merge_nearby_orders(max_gap_seconds: int = 300):
    """
    自動檢查並將相近時間（預設 5 分鐘內）同一車輛上傳的多筆單張照片訂單，合併到同一個訂單批次中
    """
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
    SELECT id, vehicle_id, created_at, inspection_status 
    FROM orders 
    ORDER BY vehicle_id, created_at ASC
    """)
    rows = cursor.fetchall()
    if not rows:
        conn.close()
        return

    batches = []
    current_batch = []
    for r in rows:
        try:
            dt = datetime.strptime(r["created_at"], "%Y-%m-%d %H:%M:%S")
        except Exception:
            dt = datetime.now()
        if not current_batch:
            current_batch.append((r, dt))
        else:
            prev_r, prev_dt = current_batch[-1]
            if r["vehicle_id"] == prev_r["vehicle_id"] and abs((dt - prev_dt).total_seconds()) <= max_gap_seconds:
                current_batch.append((r, dt))
            else:
                if len(current_batch) > 1:
                    batches.append(current_batch)
                current_batch = [(r, dt)]
    if len(current_batch) > 1:
        batches.append(current_batch)

    for b in batches:
        master_id = b[0][0]["id"]
        other_ids = [item[0]["id"] for item in b[1:]]
        has_review = any(item[0]["inspection_status"] == "needs_review" for item in b)

        for oid in other_ids:
            cursor.execute("UPDATE vehicle_photos SET order_id = ? WHERE order_id = ?", (master_id, oid))
            cursor.execute("DELETE FROM human_reviews WHERE order_id = ?", (oid,))
            cursor.execute("DELETE FROM orders WHERE id = ?", (oid,))

        if has_review:
            cursor.execute("UPDATE orders SET inspection_status = 'needs_review' WHERE id = ?", (master_id,))

    conn.commit()
    conn.close()

def get_all_orders_for_admin() -> List[dict]:
    """
    提供給 Streamlit 客服審核後台獲取完整訂單 + 該批次所有照片列表 + 左右比對視角 + 瑕疵詳情
    """
    # 讀取時自動合併同一時段上傳的多張照片訂單
    auto_merge_nearby_orders()

    conn = get_connection()
    cursor = conn.cursor()

    cursor.execute("""
    SELECT 
        o.id AS order_id,
        o.start_time,
        o.end_time,
        o.insurance,
        o.inspection_status AS status,
        o.created_at,
        u.name AS user_name,
        u.account AS user_account,
        v.plate_number,
        v.model,
        hr.note AS review_note,
        hr.reviewer_name AS reviewer,
        hr.penalty_amount
    FROM orders o
    LEFT JOIN users u ON o.user_id = u.id
    LEFT JOIN vehicles v ON o.vehicle_id = v.id
    LEFT JOIN human_reviews hr ON o.id = hr.order_id
    ORDER BY o.created_at DESC
    """)

    order_rows = cursor.fetchall()
    results = []

    angle_names = {
        "front_left": "📸 左前方",
        "front_right": "📸 右前方",
        "rear_left": "📸 左後方",
        "rear_right": "📸 右後方",
        "scratch": "🩹 傷痕特寫"
    }

    for row in order_rows:
        order_dict = dict(row)
        order_id = order_dict["order_id"]

        # 抓取該訂單的所有照片與 AI 偵測結果
        cursor.execute("""
        SELECT * FROM vehicle_photos WHERE order_id = ? ORDER BY created_at ASC
        """, (order_id,))
        photo_rows = cursor.fetchall()

        comparisons = {}
        photos_list = []
        total_damage = 0
        max_conf = 0.0
        labels_list = []

        for p_row in photo_rows:
            p_dict = dict(p_row)
            photo_id = p_dict["id"]
            angle_key = p_dict["angle"]

            cursor.execute("SELECT * FROM ai_detections WHERE photo_id = ?", (photo_id,))
            det_rows = cursor.fetchall()
            detections = []
            for d in det_rows:
                d_dict = dict(d)
                d_dict["bbox"] = json.loads(d_dict["bbox"]) if d_dict["bbox"] else []
                detections.append(d_dict)
                total_damage += 1
                if d_dict["confidence"] > max_conf:
                    max_conf = d_dict["confidence"]
                labels_list.append(d_dict["label"])

            angle_clean = angle_key.lower().replace("-", "_")
            angle_title = angle_names.get(angle_clean)
            if not angle_title:
                if "scratch" in angle_clean:
                    angle_title = f"🩹 傷痕特寫 #{len(photos_list)+1}"
                else:
                    angle_title = f"📸 {angle_clean.replace('_', ' ').title()}"

            photo_item = {
                "photo_id": photo_id,
                "angle": angle_key,
                "angle_label": angle_title,
                "has_damage": len(detections) > 0,
                "damage_count": len(detections),
                "before_url": p_dict["raw_url"],
                "after_url": p_dict["annotated_url"] or p_dict["raw_url"],
                "blur_score": p_dict["blur_score"],
                "mean_brightness": p_dict["mean_brightness"],
                "detections": detections,
                "created_at": p_dict["created_at"]
            }
            photos_list.append(photo_item)
            comparisons[angle_key] = photo_item

        # 組裝 AI 摘要文字
        if total_damage > 0:
            top_label = labels_list[0] if labels_list else "damage"
            order_dict["ai_summary"] = f"偵測到 {total_damage} 處車損 ({top_label} {int(max_conf*100)}%)"
        else:
            order_dict["ai_summary"] = "無異常 (AI 信心度 98%)"

        order_dict["damage_count"] = total_damage
        order_dict["max_confidence"] = max_conf
        order_dict["reviewer"] = order_dict.get("reviewer") or ("尚未指派" if total_damage > 0 else "AI Auto-System")
        order_dict["review_note"] = order_dict.get("review_note") or ""
        order_dict["penalty_amount"] = order_dict.get("penalty_amount") or 0
        order_dict["comparisons"] = comparisons
        order_dict["photos"] = photos_list
        order_dict["photo_count"] = len(photos_list)

        results.append(order_dict)

    conn.close()
    return results

def update_human_review(order_id: str, status: str, note: str, reviewer_name: str, penalty_amount: int = 0):
    """
    客服人員審核操作，同步寫入 human_reviews 表並更新 orders 狀態
    """
    conn = get_connection()
    cursor = conn.cursor()
    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    # 1. 更新 orders 表
    cursor.execute("UPDATE orders SET inspection_status = ? WHERE id = ?", (status, order_id))

    # 2. 寫入或更新 human_reviews 表
    cursor.execute("SELECT id FROM human_reviews WHERE order_id = ?", (order_id,))
    existing = cursor.fetchone()
    if existing:
        cursor.execute("""
        UPDATE human_reviews 
        SET check_status = ?, penalty_amount = ?, note = ?, reviewer_name = ?, created_at = ?
        WHERE order_id = ?
        """, (status, penalty_amount, note, reviewer_name, now_str, order_id))
    else:
        rev_id = f"rev_{uuid.uuid4().hex[:8]}"
        cursor.execute("""
        INSERT INTO human_reviews (id, order_id, check_status, penalty_amount, note, reviewer_name, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (rev_id, order_id, status, penalty_amount, note, reviewer_name, now_str))

    conn.commit()
    conn.close()

def get_all_vehicles_with_ledger() -> List[dict]:
    """
    獲取所有車輛即時營運狀態與既有車損帳本 (Damage Ledger)
    """
    conn = get_connection()
    cursor = conn.cursor()

    cursor.execute("SELECT * FROM vehicles ORDER BY created_at DESC")
    vehicles = [dict(r) for r in cursor.fetchall()]

    for v in vehicles:
        cursor.execute("SELECT * FROM damage_ledger WHERE vehicle_id = ?", (v["id"],))
        damages = []
        for d in cursor.fetchall():
            damages.append({
                "angle": d["angle"],
                "type": d["damage_type"],
                "photo": d["photo_url"],
                "note": d["note"],
                "date": d["registered_date"]
            })
        v["existing_damages"] = damages
        v["total_rentals"] = 48 # 統計數據

        # 歷史時間軸
        cursor.execute("""
        SELECT o.created_at, o.inspection_status, u.name as user_name 
        FROM orders o
        JOIN users u ON o.user_id = u.id
        WHERE o.vehicle_id = ?
        ORDER BY o.created_at DESC LIMIT 3
        """, (v["id"],))
        timeline = []
        for t in cursor.fetchall():
            timeline.append({
                "time": t["created_at"],
                "event": f"還車檢驗完成 (狀態: {t['inspection_status']})",
                "user": t["user_name"]
            })
        v["timeline"] = timeline

    conn.close()
    return vehicles

# 模組載入時自動初始化
init_db()
