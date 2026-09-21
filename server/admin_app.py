import streamlit as st
import pandas as pd
import numpy as np
import plotly.express as px
import plotly.graph_objects as go
from datetime import datetime, timedelta
import json
import requests
import database

# ==============================================================================
# 1. 頁面全域配置與現代化 UI 樣式 (Tailwind / Shadcn 風格)
# ==============================================================================
st.set_page_config(
    page_title="CarGuard AI - 車況檢驗與客服審核後台",
    page_icon="🚗",
    layout="wide",
    initial_sidebar_state="expanded"
)

st.markdown("""
<style>
    @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');
    
    html, body, [class*="css"] {
        font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    }
    
    .stApp {
        background-color: #f8fafc;
    }
    
    .header-box {
        background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
        padding: 24px 32px;
        border-radius: 16px;
        color: white;
        margin-bottom: 24px;
        box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1);
    }
    .header-title {
        font-size: 26px;
        font-weight: 700;
        margin: 0;
        letter-spacing: -0.5px;
    }
    .header-subtitle {
        color: #94a3b8;
        font-size: 14px;
        margin-top: 6px;
    }

    .kpi-card {
        background: white;
        border-radius: 14px;
        padding: 20px;
        border: 1px solid #e2e8f0;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
        transition: transform 0.2s ease, box-shadow 0.2s ease;
    }
    .kpi-card:hover {
        transform: translateY(-2px);
        box-shadow: 0 8px 20px rgba(0, 0, 0, 0.06);
    }
    .kpi-title {
        font-size: 13px;
        color: #64748b;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.5px;
    }
    .kpi-value {
        font-size: 28px;
        font-weight: 700;
        color: #0f172a;
        margin: 8px 0 4px 0;
    }
    .kpi-delta {
        font-size: 12px;
        font-weight: 500;
    }
    .kpi-positive { color: #10b981; }
    .kpi-neutral { color: #3b82f6; }
    .kpi-negative { color: #ef4444; }

    .badge {
        display: inline-block;
        padding: 4px 10px;
        border-radius: 9999px;
        font-size: 12px;
        font-weight: 600;
        text-align: center;
    }
    .badge-pass { background-color: #dcfce7; color: #15803d; border: 1px solid #bbf7d0; }
    .badge-review { background-color: #fef9c3; color: #a16207; border: 1px solid #fef08a; }
    .badge-damage { background-color: #fee2e2; color: #b91c1c; border: 1px solid #fecaca; }
    .badge-dispute { background-color: #f3e8ff; color: #7e22ce; border: 1px solid #e9d5ff; }

    .workbench-card {
        background: white;
        border-radius: 14px;
        border: 1px solid #e2e8f0;
        padding: 20px;
        box-shadow: 0 2px 8px rgba(0,0,0,0.03);
        margin-bottom: 16px;
    }

    footer {visibility: hidden;}
    .block-container {
        padding-top: 1.8rem;
        padding-bottom: 2rem;
        max-width: 1400px;
    }
</style>
""", unsafe_allow_html=True)


# ==============================================================================
# 2. 側邊欄導航 (Navigation) 與即時資料庫讀取
# ==============================================================================
with st.sidebar:
    st.markdown("""
    <div style="display:flex; align-items:center; gap:12px; margin-bottom: 20px;">
        <span style="font-size: 32px;">🛡️</span>
        <div>
            <h2 style="margin:0; font-size:18px; font-weight:700; color:#0f172a;">CarGuard AI</h2>
            <span style="font-size:12px; color:#64748b;">智能車損檢驗資料庫後台 v3.0</span>
        </div>
    </div>
    """, unsafe_allow_html=True)

    nav_choice = st.radio(
        "系統導航選單",
        ["📊 數據看板 (Dashboard)", "📋 訂單與審核工作台 (Review)", "🚗 車輛追蹤與履歷 (Damage Ledger)"],
        label_visibility="collapsed"
    )

    st.markdown("---")
    st.caption("🗄️ 資料庫連線狀態")
    
    if st.button("🔄 立即同步資料庫 (最新案件)", width="stretch", type="primary"):
        st.rerun()

    # 直接從 database.py 讀取持久化資料庫
    all_orders = database.get_all_orders_for_admin()
    all_vehicles = database.get_all_vehicles_with_ledger()
    st.session_state.orders = all_orders
    st.session_state.vehicles = all_vehicles

    st.markdown(f"""
    <div style="background:#ecfdf5; border:1px solid #a7f3d0; padding:10px; border-radius:8px; font-size:12px; color:#065f46;">
        🟢 <b>SQLite 資料庫連線正常</b><br>
        • 累計訂單數：<b>{len(all_orders)}</b> 筆<br>
        • 管理車輛數：<b>{len(all_vehicles)}</b> 輛
    </div>
    """, unsafe_allow_html=True)

    st.markdown("---")
    st.markdown("""
    <div style="background:#f1f5f9; padding:12px; border-radius:8px; font-size:12px; color:#475569;">
        👤 當前審核員：<b>客服一組 - 專員 08</b><br>
        🕒 系統時間：<b>2026-08-31 13:45</b>
    </div>
    """, unsafe_allow_html=True)


# ==============================================================================
# 頁面 1: 數據看板 (Dashboard)
# ==============================================================================
if nav_choice == "📊 數據看板 (Dashboard)":
    st.markdown("""
    <div class="header-box">
        <h1 class="header-title">📊 智能車損檢驗數據看板 (POC Dashboard)</h1>
        <div class="header-subtitle">即時監控 AI 影像辨識直通率、客服複審覆核率與瑕疵類型分佈趨勢</div>
    </div>
    """, unsafe_allow_html=True)

    # 頂部 KPI 指標卡 (依據 PDF 頁面 1 規格)
    k1, k2, k3, k4 = st.columns(4)
    with k1:
        st.markdown("""
        <div class="kpi-card">
            <div class="kpi-title">🚀 自動通過率 (Auto-Pass)</div>
            <div class="kpi-value">78.4%</div>
            <div class="kpi-delta kpi-positive">↑ 4.2% 免人工審核直通</div>
        </div>
        """, unsafe_allow_html=True)

    with k2:
        st.markdown("""
        <div class="kpi-card">
            <div class="kpi-title">🎯 AI / 複審一致率</div>
            <div class="kpi-value">95.6%</div>
            <div class="kpi-delta kpi-positive">↑ 1.8% 審核人員認同率</div>
        </div>
        """, unsafe_allow_html=True)

    with k3:
        st.markdown("""
        <div class="kpi-card">
            <div class="kpi-title">⚠️ 誤報率 (False Positive)</div>
            <div class="kpi-value">3.8%</div>
            <div class="kpi-delta kpi-neutral">↓ 0.5% (反光/光影過濾優化)</div>
        </div>
        """, unsafe_allow_html=True)

    with k4:
        st.markdown("""
        <div class="kpi-card">
            <div class="kpi-title">🔍 漏檢率 (False Negative)</div>
            <div class="kpi-value">0.6%</div>
            <div class="kpi-delta kpi-positive">極低漏檢風險</div>
        </div>
        """, unsafe_allow_html=True)

    st.markdown("<br>", unsafe_allow_html=True)

    # 視覺化圖表區域 (圓餅圖 + 趨勢圖)
    c1, c2 = st.columns([1, 1.4])
    
    with c1:
        st.markdown("### 🧩 瑕疵類型分佈統計 (AI Detections 表分析)")
        defect_data = pd.DataFrame({
            "瑕疵類型": ["刮痕 (Scratch)", "凹痕 (Dent)", "髒污/垃圾 (Dirty/Trash)", "零件缺損 (Missing)", "破損裂痕 (Broken)"],
            "數量": [142, 48, 65, 23, 14]
        })
        fig_pie = px.pie(
            defect_data,
            values="數量",
            names="瑕疵類型",
            color_discrete_sequence=["#3b82f6", "#ef4444", "#f59e0b", "#8b5cf6", "#10b981"],
            hole=0.45
        )
        fig_pie.update_layout(
            margin=dict(l=20, r=20, t=20, b=20),
            legend=dict(orientation="h", yanchor="bottom", y=-0.2, xanchor="center", x=0.5)
        )
        st.plotly_chart(fig_pie, width="stretch")

    with c2:
        st.markdown("### 📈 近 7 日檢驗案件量趨勢 (AI 直通 vs 人工介入)")
        days = [(datetime.now() - timedelta(days=i)).strftime("%m/%d") for i in range(6, -1, -1)]
        trend_df = pd.DataFrame({
            "日期": days,
            "AI 自動直通 (件)": [85, 92, 78, 110, 125, 140, 118],
            "待人工複審 (件)": [24, 18, 22, 28, 30, 25, 26]
        })
        fig_bar = go.Figure()
        fig_bar.add_trace(go.Bar(x=trend_df["日期"], y=trend_df["AI 自動直通 (件)"], name="AI 直通 (Auto-Pass)", marker_color="#10b981"))
        fig_bar.add_trace(go.Bar(x=trend_df["日期"], y=trend_df["待人工複審 (件)"], name="人工介入 (Needs Review)", marker_color="#f59e0b"))
        fig_bar.update_layout(
            barmode="stack",
            margin=dict(l=20, r=20, t=20, b=20),
            legend=dict(orientation="h", yanchor="bottom", y=1.02, xanchor="right", x=1),
            plot_bgcolor="rgba(0,0,0,0)",
            yaxis=dict(gridcolor="#e2e8f0")
        )
        st.plotly_chart(fig_bar, width="stretch")


# ==============================================================================
# 頁面 2: 訂單與審核工作台 (Inspection Review Workbench)
# ==============================================================================
elif nav_choice == "📋 訂單與審核工作台 (Review)":
    st.markdown("""
    <div class="header-box">
        <h1 class="header-title">📋 訂單車況檢驗與客服審核工作台</h1>
        <div class="header-subtitle">讀取 Orders 與 VehiclePhotos 資料庫記錄，提供 Before vs. After 雙視窗對照與審核判定</div>
    </div>
    """, unsafe_allow_html=True)

    # 頂部篩選器
    f1, f2, f3 = st.columns([1.5, 1.5, 1])
    with f1:
        status_filter = st.selectbox(
            "狀態篩選",
            ["全部 (All)", "待人工複審 (Needs Review)", "AI 通過 (Auto-Passed)", "已確認扣款 (Confirmed)", "爭議中 (Disputed)"]
        )
    with f2:
        search_query = st.text_input("🔍 搜尋車牌號碼 / 訂單 ID / 會員帳號", "")
    with f3:
        st.write("")
        st.write("")
        st.caption(f"共找到 {len(st.session_state.orders)} 筆案件記錄")

    status_map = {
        "待人工複審 (Needs Review)": "needs_review",
        "AI 通過 (Auto-Passed)": "auto_passed",
        "已確認扣款 (Confirmed)": "confirmed",
        "爭議中 (Disputed)": "disputed"
    }

    filtered_orders = st.session_state.orders
    if status_filter != "全部 (All)":
        target_code = status_map[status_filter]
        filtered_orders = [o for o in filtered_orders if o["status"] == target_code]
    if search_query:
        filtered_orders = [
            o for o in filtered_orders 
            if search_query.lower() in o["order_id"].lower() 
            or search_query.lower() in (o.get("plate_number") or "").lower() 
            or search_query.lower() in (o.get("user_name") or "").lower()
        ]

    st.markdown("### 📑 案件清單 (點選進入審核)")
    
    for idx, order in enumerate(filtered_orders):
        if order["status"] == "auto_passed":
            badge_html = '<span class="badge badge-pass">✅ AI 通過</span>'
        elif order["status"] == "needs_review":
            badge_html = '<span class="badge badge-review">⚠️ 待人工複審</span>'
        elif order["status"] == "confirmed":
            badge_html = '<span class="badge badge-damage">🚨 已確認扣款</span>'
        else:
            badge_html = '<span class="badge badge-dispute">⚖️ 爭議中</span>'

        with st.expander(f"{order['order_id']} ｜ 車牌：{order.get('plate_number', '未知')} ({order.get('model', '車輛')}) ｜ 會員：{order.get('user_name', '租借人')}", expanded=(idx==0 and order["status"]=="needs_review")):
            col_info, col_btn = st.columns([4, 1])
            with col_info:
                st.markdown(f"""
                - **租借時段**：`{order['start_time']}` 至 `{order['end_time']}` ｜ **加購安心保險**：{'🛡️ 有' if order.get('insurance', True) else '❌ 無'}
                - **AI 偵測結果**：**{order.get('ai_summary', '無異常')}**
                - **複審狀態**：{badge_html} ｜ **處理人員**：`{order.get('reviewer', '尚未指派')}`
                - **審核備註**：_{order.get('review_note') or '尚無備註'}_
                """, unsafe_allow_html=True)
            with col_btn:
                if st.button("🔍 進入審核工作台", key=f"btn_review_{order['order_id']}", width="stretch", type="primary" if order["status"]=="needs_review" else "secondary"):
                    st.session_state.selected_order_id = order["order_id"]

    # ==============================================================================
    # 審核工作台詳情 (Before vs After Diff Viewer)
    # ==============================================================================
    selected_id = st.session_state.get("selected_order_id", filtered_orders[0]["order_id"] if filtered_orders else None)
    active_order = next((o for o in st.session_state.orders if o["order_id"] == selected_id), None)

    if active_order and active_order.get("comparisons"):
        st.markdown("---")
        st.markdown(f"## 🛠️ 案件詳細審核工作台：`{active_order['order_id']}`")
        
        st.markdown(f"""
        <div class="workbench-card">
            <div style="display:flex; justify-content:space-between; align-items:center;">
                <div>
                    <h3 style="margin:0; font-size:18px;">🚗 {active_order.get('plate_number', '未知')} ｜ {active_order.get('model', '車輛')}</h3>
                    <span style="color:#64748b; font-size:13px;">租借人：{active_order.get('user_name', '租借人')} ｜ 還車時間：{active_order['end_time']} ｜ 保險：{'已投保' if active_order.get('insurance', True) else '未投保'}</span>
                </div>
                <div>
                    <span style="font-size:13px; color:#64748b;">AI 標籤：</span> <b>{active_order.get('ai_summary', '')}</b>
                </div>
            </div>
        </div>
        """, unsafe_allow_html=True)

        comparisons = active_order.get("comparisons", {})
        active_angle_key = list(comparisons.keys())[0] if comparisons else "front_left"
        comp = comparisons.get(active_angle_key, {})

        view_col, action_col = st.columns([2.2, 1])
        
        with view_col:
            st.markdown("#### 🔄 左右對照檢視區 (Before vs. After Diff)")
            toggle_bbox = st.toggle("顯示 AI 辨識框與熱區遮罩 (Bounding Box Overlay)", value=True)
            
            img_c1, img_c2 = st.columns(2)
            with img_c1:
                st.caption("📸 1. 取車前照片 (Before)")
                before_img = comp.get("before_url") or "https://images.unsplash.com/photo-1549399542-7e3f8b79c341?w=800"
                st.image(before_img, width="stretch")
            with img_c2:
                st.caption("📸 2. 還車後照片 (After + AI 偵測)")
                after_img = comp.get("after_url") or comp.get("before_url")
                st.image(after_img, width="stretch")
                
                if toggle_bbox and comp.get("detections"):
                    det_lines = []
                    for d in comp.get("detections", []):
                        det_lines.append(f"• 🔴 <b>[{d.get('label')}]</b> 信心度 {int(d.get('confidence', 0.9)*100)}%")
                    
                    st.markdown(f"""
                    <div style="background:#fee2e2; border:1px solid #f87171; border-radius:8px; padding:10px; margin-top:8px;">
                        <span style="color:#b91c1c; font-size:13px; font-weight:600;">🚨 AI 偵測疊加層：</span><br>
                        <span style="color:#7f1d1d; font-size:12px;">{'<br>'.join(det_lines)}</span>
                    </div>
                    """, unsafe_allow_html=True)

        with action_col:
            st.markdown("#### ✍️ 審核操作面板 (Action Panel)")
            
            st.markdown(f"""
            <div style="background:#f8fafc; padding:12px; border-radius:8px; border:1px solid #e2e8f0; margin-bottom:12px;">
                <div style="font-size:13px; font-weight:600; color:#334155;">🤖 AI 判斷摘要：</div>
                <div style="font-size:12px; color:#64748b; margin-top:4px;">
                    • 偵測瑕疵數：<b>{active_order.get('damage_count', 0)} 處</b><br>
                    • 最高信心度：<b>{int(active_order.get('max_confidence', 0.9)*100)}%</b><br>
                    • 判定結果：<b>{'建議人工複審' if active_order.get('damage_count', 0)>0 else '符合自動通過標準'}</b>
                </div>
            </div>
            """, unsafe_allow_html=True)

            penalty_input = st.number_input("求償/清潔維修費用 (NT$)", min_value=0, value=2500, step=500)
            resp_choice = st.selectbox("責任歸屬判定", ["租借人過失 (User Fault)", "既有舊傷誤判 (False Positive)", "第三方/不明 (Uncertain)"])
            review_note = st.text_area("審核備註記錄", value=f"經客服專員核對，確認為本趟檢驗車況。")

            b1, b2, b3 = st.columns(3)
            with b1:
                if st.button("✅ 確認車損扣款", type="primary", width="stretch"):
                    database.update_human_review(
                        order_id=active_order["order_id"],
                        status="confirmed",
                        note=f"【確認車損】收取 {penalty_input} 元 ({review_note})",
                        reviewer_name="客服專員 08",
                        penalty_amount=penalty_input
                    )
                    st.success("已完成扣款結單！(資料庫已同步更新)")
                    st.rerun()
            with b2:
                if st.button("🟢 標記誤判/免賠", width="stretch"):
                    database.update_human_review(
                        order_id=active_order["order_id"],
                        status="auto_passed",
                        note=f"【判定免賠】{review_note}",
                        reviewer_name="客服專員 08"
                    )
                    st.info("已標記為 False Positive 並結案！(資料庫已同步更新)")
                    st.rerun()
            with b3:
                if st.button("⏸️ 待申訴/補件", width="stretch"):
                    database.update_human_review(
                        order_id=active_order["order_id"],
                        status="disputed",
                        note=f"【暫緩爭議】{review_note}",
                        reviewer_name="客服專員 08"
                    )
                    st.warning("已轉入爭議流程！(資料庫已同步更新)")
                    st.rerun()


# ==============================================================================
# 頁面 3: 後續車輛追蹤介面 (Vehicle Tracking & Damage Ledger)
# ==============================================================================
elif nav_choice == "🚗 車輛追蹤與履歷 (Damage Ledger)":
    st.markdown("""
    <div class="header-box">
        <h1 class="header-title">🚗 車輛資產即時狀態與歷史瑕疵帳本 (Damage Ledger)</h1>
        <div class="header-subtitle">讀取 Vehicles 與 Damage_Ledger 資料庫資料，追蹤每輛車營運狀態與既有舊傷</div>
    </div>
    """, unsafe_allow_html=True)

    v_stat1, v_stat2, v_stat3, v_stat4 = st.columns(4)
    v_stat1.metric("🟢 可租借 (Activate)", "18 輛", "+2 輛")
    v_stat2.metric("🔵 租借中 (In Rent)", "24 輛", "+5 輛")
    v_stat3.metric("🟡 清潔中 (Cleaning)", "3 輛", "-1 輛")
    v_stat4.metric("🔴 維修中 (Fixing)", "2 輛", "0 輛")

    st.markdown("### 🚘 車隊車輛清單 (來自 Vehicles 資料表)")

    for veh in st.session_state.vehicles:
        with st.expander(f"🚗 {veh['plate_number']} ｜ {veh['model']} ｜ 狀態：{veh['status'].upper()}", expanded=True):
            col_v1, col_v2 = st.columns([1.2, 1])
            
            with col_v1:
                st.markdown("#### 📜 既有車損歷史清單 (避免重複求償)")
                if veh.get("existing_damages"):
                    for d in veh["existing_damages"]:
                        st.markdown(f"""
                        <div style="display:flex; gap:16px; background:#fff7ed; padding:12px; border-radius:10px; border:1px solid #ffedd5; margin-bottom:8px;">
                            <div style="font-size:24px;">🩹</div>
                            <div>
                                <div style="font-weight:700; color:#9a3412; font-size:14px;">位置：{d['angle']} ｜ 類型：{d['type']}</div>
                                <div style="color:#c2410c; font-size:12px;">登記日期：{d['date']} ｜ 備註：{d['note']}</div>
                            </div>
                        </div>
                        """, unsafe_allow_html=True)
                else:
                    st.info("✨ 此車輛目前全車外觀完好，無登記既有傷痕。")

            with col_v2:
                st.markdown("#### ⏳ 歷史維修與檢驗時間軸 (Timeline)")
                if veh.get("timeline"):
                    for t in veh["timeline"]:
                        st.markdown(f"""
                        <div style="border-left: 3px solid #3b82f6; padding-left: 12px; margin-bottom: 12px;">
                            <span style="font-size:12px; color:#64748b;">🕒 {t['time']}</span><br>
                            <span style="font-weight:600; font-size:13px; color:#1e293b;">{t['event']}</span><br>
                            <span style="font-size:12px; color:#475569;">操作者/會員：{t['user']}</span>
                        </div>
                        """, unsafe_allow_html=True)
                else:
                    st.caption("尚無歷史維修紀錄。")
