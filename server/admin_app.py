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
    
    html, body, [class*="css"], .stApp {
        font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
        background-color: #f8fafc !important;
        color: #0f172a !important;
    }
    
    /* 徹底防護白底白字：全域段落、標籤、文字一律設為深灰黑高對比 */
    p, span, label, li, dt, dd, .stMarkdown, .stText, [data-testid="stMarkdownContainer"] p {
        color: #1e293b !important;
    }
    h1, h2, h3, h4, h5, h6 {
        color: #0f172a !important;
        font-weight: 700 !important;
    }
    b, strong {
        color: #0f172a !important;
        font-weight: 700 !important;
    }
    
    /* 頂部 Header Box (深藍底 + 明亮白字) */
    .header-box {
        background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%) !important;
        padding: 24px 32px;
        border-radius: 16px;
        margin-bottom: 24px;
        box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1);
    }
    .header-box * {
        color: #ffffff !important;
    }
    .header-title {
        font-size: 26px;
        font-weight: 700;
        margin: 0;
        letter-spacing: -0.5px;
    }
    .header-subtitle {
        color: #cbd5e1 !important;
        font-size: 14px;
        margin-top: 6px;
    }

    /* 指標卡片 (KPI Card) */
    .kpi-card {
        background: #ffffff !important;
        border-radius: 14px;
        padding: 20px;
        border: 1px solid #cbd5e1 !important;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
        transition: transform 0.2s ease, box-shadow 0.2s ease;
    }
    .kpi-card:hover {
        transform: translateY(-2px);
        box-shadow: 0 8px 20px rgba(0, 0, 0, 0.06);
    }
    .kpi-title {
        font-size: 13px;
        color: #475569 !important;
        font-weight: 700 !important;
        text-transform: uppercase;
        letter-spacing: 0.5px;
    }
    .kpi-value {
        font-size: 28px;
        font-weight: 800 !important;
        color: #0f172a !important;
        margin: 8px 0 4px 0;
    }
    .kpi-delta {
        font-size: 12px;
        font-weight: 600 !important;
    }
    .kpi-positive { color: #166534 !important; }
    .kpi-neutral { color: #1d4ed8 !important; }
    .kpi-negative { color: #b91c1c !important; }

    /* 狀態標籤 (Badges) */
    .badge {
        display: inline-block;
        padding: 4px 12px;
        border-radius: 9999px;
        font-size: 12px;
        font-weight: 700 !important;
        text-align: center;
    }
    .badge-pass { background-color: #dcfce7 !important; color: #14532d !important; border: 1px solid #86efac !important; }
    .badge-review { background-color: #fef9c3 !important; color: #713f12 !important; border: 1px solid #fde047 !important; }
    .badge-damage { background-color: #fee2e2 !important; color: #7f1d1d !important; border: 1px solid #fca5a5 !important; }
    .badge-dispute { background-color: #f3e8ff !important; color: #581c87 !important; border: 1px solid #d8b4fe !important; }

    /* 工作台卡片 (Workbench Card) */
    .workbench-card {
        background: #ffffff !important;
        border-radius: 14px;
        border: 1px solid #cbd5e1 !important;
        padding: 20px;
        box-shadow: 0 2px 8px rgba(0,0,0,0.03);
        margin-bottom: 16px;
    }
    .workbench-card * {
        color: #0f172a !important;
    }

    /* 摺頁 (Expander) 強制純白卡片背景與清晰深色字 */
    [data-testid="stExpander"] {
        background-color: #ffffff !important;
        border: 1.5px solid #cbd5e1 !important;
        border-radius: 12px !important;
        box-shadow: 0 2px 6px rgba(0,0,0,0.04) !important;
        margin-bottom: 16px !important;
    }
    [data-testid="stExpander"] summary {
        background-color: #ffffff !important;
        padding: 12px 16px !important;
    }
    [data-testid="stExpander"] summary * {
        color: #0f172a !important;
        font-size: 15px !important;
        font-weight: 700 !important;
    }
    [data-testid="stExpander"] [data-testid="stExpanderDetails"] {
        background-color: #ffffff !important;
        padding: 18px 20px !important;
        border-top: 1px solid #e2e8f0 !important;
    }
    [data-testid="stExpander"] [data-testid="stExpanderDetails"] * {
        color: #1e293b !important;
    }

    /* 分頁 Tabs 樣式 */
    .stTabs [data-baseweb="tab-list"] {
        gap: 8px;
        background-color: #f1f5f9 !important;
        padding: 6px;
        border-radius: 10px;
    }
    .stTabs [data-baseweb="tab"] {
        border-radius: 8px;
        padding: 6px 16px;
        color: #475569 !important;
        font-weight: 600;
    }
    .stTabs [aria-selected="true"] {
        background-color: #ffffff !important;
        color: #0f172a !important;
        box-shadow: 0 1px 4px rgba(0,0,0,0.08);
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

    st.markdown("### 📑 案件清單 (同一批上傳圖片集中在同一個摺頁內)")
    
    for idx, order in enumerate(filtered_orders):
        if order["status"] == "auto_passed":
            badge_html = '<span class="badge badge-pass">✅ AI 通過</span>'
        elif order["status"] == "needs_review":
            badge_html = '<span class="badge badge-review">⚠️ 待人工複審</span>'
        elif order["status"] == "confirmed":
            badge_html = '<span class="badge badge-damage">🚨 已確認扣款</span>'
        else:
            badge_html = '<span class="badge badge-dispute">⚖️ 爭議中</span>'

        photos = order.get("photos", [])
        photo_count = len(photos)
        expander_title = f"{order['order_id']} ｜ 車牌：{order.get('plate_number', '未知')} ({order.get('model', '車輛')}) ｜ 📸 包含 {photo_count} 張檢驗照片 ｜ 會員：{order.get('user_name', '租借人')}"

        with st.expander(expander_title, expanded=(idx == 0 and order["status"] == "needs_review")):
            # 1. 訂單基本資訊卡
            st.markdown(f"""
            <div style="background:#ffffff; border:1px solid #cbd5e1; border-radius:10px; padding:16px; margin-bottom:16px; box-shadow:0 1px 3px rgba(0,0,0,0.04);">
                <div style="display:flex; justify-content:space-between; align-items:flex-start; flex-wrap:wrap; gap:10px;">
                    <div>
                        <div style="font-size:17px; font-weight:700; color:#0f172a;">🚗 {order.get('plate_number', '未知')} ｜ {order.get('model', '車輛')}</div>
                        <div style="font-size:13px; color:#334155; margin-top:4px;">
                            👤 租借人：<b style="color:#0f172a;">{order.get('user_name', '租借人')}</b> ({order.get('user_account', '')}) ｜ 
                            🕒 租借時段：<code style="color:#0f172a; font-weight:600;">{order['start_time']}</code> 至 <code style="color:#0f172a; font-weight:600;">{order['end_time']}</code> ｜ 
                            🛡️ 保險：<b style="color:#0f172a;">{'已投保安心險' if order.get('insurance', True) else '未投保'}</b>
                        </div>
                    </div>
                    <div style="text-align:right;">
                        <span style="font-size:13px; color:#475569; font-weight:600;">案件狀態：</span> {badge_html}<br>
                        <span style="font-size:12px; color:#64748b;">處理專員：<b style="color:#0f172a;">{order.get('reviewer', '尚未指派')}</b></span>
                    </div>
                </div>
                <div style="margin-top:10px; padding-top:10px; border-top:1px solid #e2e8f0; font-size:13px; color:#1e293b;">
                    🤖 <b>AI 綜合判定</b>：<span style="font-weight:700; color:{'#b91c1c' if order.get('damage_count',0)>0 else '#15803d'};">{order.get('ai_summary', '無異常')}</span>
                    {(' ｜ ✍️ <b>審核備註</b>：<span style="color:#334155; font-style:italic;">' + order.get('review_note') + '</span>') if order.get('review_note') else ''}
                </div>
            </div>
            """, unsafe_allow_html=True)

            # 2. 同一批上傳的圖片集中在此摺頁展示
            if photos:
                st.markdown(f"#### 📸 本次檢驗照片清單（共 {len(photos)} 張）")
                tab_titles = [f"{p['angle_label']} {'🚨 ' + str(p['damage_count']) + ' 處瑕疵' if p['has_damage'] else '🟢 良好'}" for p in photos]
                tabs = st.tabs(tab_titles)
                
                for p_idx, p in enumerate(photos):
                    with tabs[p_idx]:
                        col_img, col_detail = st.columns([1.8, 1.2])
                        with col_img:
                            toggle_key = f"toggle_bbox_{order['order_id']}_{p['photo_id']}"
                            show_bbox = st.toggle("顯示 AI 辨識框與熱區遮罩", value=True, key=toggle_key)
                            
                            c_before, c_after = st.columns(2)
                            with c_before:
                                st.caption("📸 1. 取車前原圖 (Before)")
                                st.image(p["before_url"], width="stretch")
                            with c_after:
                                st.caption("📸 2. 還車後 AI 辨識圖 (After)")
                                display_url = p["after_url"] if show_bbox else p["before_url"]
                                st.image(display_url, width="stretch")

                        with col_detail:
                            st.markdown(f"""
                            <div style="background:#f8fafc; border:1px solid #cbd5e1; border-radius:10px; padding:14px; margin-bottom:12px;">
                                <div style="font-weight:700; color:#0f172a; font-size:15px; margin-bottom:8px;">🔍 {p['angle_label']} 辨識指標</div>
                                <div style="font-size:13px; color:#334155; line-height:22px;">
                                    • 偵測瑕疵數量：<b style="color:{'#b91c1c' if p['damage_count']>0 else '#15803d'}; font-size:14px;">{p['damage_count']} 處</b><br>
                                    • 清晰度分數 (Laplacian)：<b style="color:#0f172a;">{p['blur_score']}</b> (良好)<br>
                                    • 畫面平均亮度：<b style="color:#0f172a;">{p['mean_brightness']}</b><br>
                                    • 上傳時間：<code style="color:#0f172a;">{p['created_at']}</code>
                                </div>
                            </div>
                            """, unsafe_allow_html=True)
                            
                            if p["detections"]:
                                det_html = "".join([f'<div style="font-size:12px; color:#7f1d1d; margin-bottom:4px; font-weight:600;">• 🔴 <b>[{d["label"]}]</b> 信心度 {int(d["confidence"]*100)}% ｜ 位置: {d.get("pos", "N/A")}</div>' for d in p["detections"]])
                                st.markdown(f"""
                                <div style="background:#fee2e2; border:1px solid #f87171; border-radius:10px; padding:12px;">
                                    <div style="color:#991b1b; font-size:13px; font-weight:700; margin-bottom:6px;">🚨 AI 偵測車損清單：</div>
                                    {det_html}
                                </div>
                                """, unsafe_allow_html=True)
                            else:
                                st.markdown("""
                                <div style="background:#f0fdf4; border:1px solid #86efac; border-radius:10px; padding:12px; font-size:13px; color:#14532d;">
                                    🟢 <b>外觀完好無異常</b>：AI 未在此照片中偵測到明顯擦傷或碰撞損壞。
                                </div>
                                """, unsafe_allow_html=True)
            else:
                st.info("此案件尚無上傳照片。")

            # 3. 摺頁內直接進行審核操作 (快速結案)
            st.markdown("---")
            st.markdown("##### ✍️ 客服快速審核操作面板")
            a1, a2, a3 = st.columns([1, 1, 1.5])
            with a1:
                pen_amt = st.number_input(
                    "求償/維修費用 (NT$)",
                    min_value=0,
                    value=order.get("penalty_amount", 2500 if order.get("damage_count", 0) > 0 else 0),
                    step=500,
                    key=f"pen_amt_{order['order_id']}"
                )
            with a2:
                fault_type = st.selectbox(
                    "責任歸屬判定",
                    ["租借人過失 (User Fault)", "既有舊傷誤判 (False Positive)", "第三方/不明 (Uncertain)"],
                    key=f"fault_type_{order['order_id']}"
                )
            with a3:
                rev_text = st.text_input(
                    "審核備註",
                    value=order.get("review_note") or f"經客服專員核對該批次 {photo_count} 張照片，確認檢驗結果。",
                    key=f"rev_text_{order['order_id']}"
                )

            act_b1, act_b2, act_b3 = st.columns(3)
            with act_b1:
                if st.button("✅ 確認車損扣款", key=f"btn_pay_{order['order_id']}", type="primary", width="stretch"):
                    database.update_human_review(
                        order_id=order["order_id"],
                        status="confirmed",
                        note=f"【確認車損】收取 {pen_amt} 元 ({fault_type} - {rev_text})",
                        reviewer_name="客服專員 08",
                        penalty_amount=pen_amt
                    )
                    st.success("✅ 已確認車損並完成扣款結單！")
                    st.rerun()
            with act_b2:
                if st.button("🟢 標記誤判/免賠", key=f"btn_pass_{order['order_id']}", width="stretch"):
                    database.update_human_review(
                        order_id=order["order_id"],
                        status="auto_passed",
                        note=f"【判定免賠】{fault_type} - {rev_text}",
                        reviewer_name="客服專員 08"
                    )
                    st.info("🟢 已標記為免賠並結案！")
                    st.rerun()
            with act_b3:
                if st.button("⏸️ 待申訴/補件", key=f"btn_disp_{order['order_id']}", width="stretch"):
                    database.update_human_review(
                        order_id=order["order_id"],
                        status="disputed",
                        note=f"【暫緩爭議】{rev_text}",
                        reviewer_name="客服專員 08"
                    )
                    st.warning("⏸️ 已轉入爭議審查流程！")
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
