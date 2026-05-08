let chatOpen      = false;
let chatHistory   = [];
let chatIsLoading = false;

const QUICK_REPLIES_DEFAULT = [
    { label: "🏷 Tour rẻ nhất",      msg: "Cho tôi xem những tour có giá rẻ nhất hiện tại" },
    { label: "🌿 Tour Đà Lạt",       msg: "Có tour đi Đà Lạt không? Giá bao nhiêu?" },
    { label: "🏖 Tour biển",          msg: "Gợi ý tour biển đẹp nhất cho tôi" },
    { label: "👨‍👩‍👧 Tour gia đình",     msg: "Tour phù hợp cho gia đình có trẻ em" },
    { label: "⭐ Tour đánh giá cao",  msg: "Tour nào được đánh giá cao nhất?" },
    { label: "🗓 Đặt tour thế nào?", msg: "Hướng dẫn tôi cách đặt tour trên web này" },
];

function buildSystemPrompt() {
    const tourCount    = allTours?.length || 0;
    const destinations = [...new Set((allTours || []).map((t) => t.destination).filter(Boolean))].join(", ");
    const cheapest     = [...(allTours || [])].sort((a, b) => a.price - b.price).slice(0, 3)
        .map((t) => `${t.name} (${Number(t.price).toLocaleString("vi-VN")}đ)`).join(", ");

    return `Bạn là trợ lý AI của nền tảng du lịch SmartTravel. Hãy trả lời bằng ngôn ngữ mà người dùng đang dùng (Tiếng Việt/English/中文/한국어/日本語).

THÔNG TIN NỀN TẢNG:
- Tên: SmartTravel — nền tảng đặt tour du lịch cao cấp
- Số tour hiện có: ${tourCount} tour
- Điểm đến: ${destinations || "nhiều tỉnh thành Việt Nam"}
- Tour giá rẻ nhất: ${cheapest || "đang cập nhật"}

NHIỆM VỤ:
1. Tư vấn tour du lịch phù hợp ngân sách, sở thích, số ngày
2. Giải thích cách đặt tour, thanh toán, hủy đơn trên SmartTravel
3. Gợi ý điểm đến hấp dẫn theo mùa
4. Trả lời câu hỏi về chính sách, voucher, điểm tích lũy

PHONG CÁCH:
- Thân thiện, nhiệt tình, chuyên nghiệp
- Câu trả lời ngắn gọn (tối đa 120 từ), dùng emoji phù hợp
- Khi gợi ý tour: nêu tên + điểm nổi bật + giá ước tính
- Luôn khuyến khích người dùng đặt tour hoặc xem danh sách

Không bịa thông tin. Nếu không biết, hướng dẫn người dùng xem trang Tours.`;
}

function appendChatMessage(role, text) {
    const container = document.getElementById("chatMessages");
    const isBot     = role === "assistant";
    const div       = document.createElement("div");
    div.className   = "chat-msg";
    div.style.cssText = `display:flex;gap:8px;align-items:flex-end;${isBot ? "" : "flex-direction:row-reverse;"}`;

    const avatar = document.createElement("div");
    avatar.style.cssText = `width:28px;height:28px;border-radius:50%;flex-shrink:0;display:flex;align-items:center;justify-content:center;font-size:13px;${isBot ? "background:linear-gradient(135deg,#22d3ee,#10b981);color:#0f172a;" : "background:rgba(99,102,241,.25);color:#a5b4fc;"}`;
    avatar.textContent = isBot ? "✦" : "👤";

    const bubble = document.createElement("div");
    bubble.style.cssText = `max-width:78%;padding:9px 13px;border-radius:${isBot ? "4px 14px 14px 14px" : "14px 4px 14px 14px"};font-size:13px;line-height:1.55;word-break:break-word;${isBot ? "background:rgba(14,116,144,.18);color:#cbd5e1;border:1px solid rgba(34,211,238,.12);" : "background:linear-gradient(135deg,rgba(34,211,238,.2),rgba(16,185,129,.15));color:#e2e8f0;border:1px solid rgba(34,211,238,.18);"}`;
    bubble.innerHTML = text
        .replace(/\*\*(.*?)\*\*/g, "<strong>$1</strong>")
        .replace(/\*(.*?)\*/g, "<em>$1</em>")
        .replace(/\n/g, "<br>");

    div.appendChild(avatar);
    div.appendChild(bubble);
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
}

function renderQuickReplies(replies) {
    replies = replies || QUICK_REPLIES_DEFAULT;
    const container = document.getElementById("quickReplies");
    container.innerHTML = "";
    replies.forEach(({ label, msg }) => {
        const btn = document.createElement("button");
        btn.className   = "qr-btn";
        btn.textContent = label;
        btn.onclick     = () => { document.getElementById("chatInput").value = msg; sendChatMessage(); };
        container.appendChild(btn);
    });
}

function renderChatWelcome() {
    document.getElementById("chatMessages").innerHTML = "";
    chatHistory = [];
    const welcomeMap = {
        vi: "Xin chào! Tôi là trợ lý AI của SmartTravel 👋\nBạn muốn đi đâu? Hãy cho tôi biết ngân sách và số ngày — tôi sẽ gợi ý tour phù hợp ngay!",
        zh: "你好！我是SmartTravel的AI助手 👋\n请告诉我您的预算和行程天数，我会立即推荐合适的旅游套餐！",
        ko: "안녕하세요! SmartTravel AI 도우미입니다 👋\n예산과 여행 일정을 알려주시면 바로 투어를 추천해드릴게요!",
        ja: "こんにちは！SmartTravel AIアシスタントです 👋\nご予算と旅行日数を教えていただければ、すぐにツアーをご提案します！",
        en: "Hello! I'm SmartTravel's AI assistant 👋\nTell me your budget and trip length — I'll suggest the perfect tour right away!"
    };
    appendChatMessage("assistant", welcomeMap[currentLang] || welcomeMap.en);
    renderQuickReplies();
}

function toggleChatbot() {
    chatOpen = !chatOpen;
    const panel     = document.getElementById("chatPanel");
    const iconOpen  = document.getElementById("chatIconOpen");
    const iconClose = document.getElementById("chatIconClose");
    const badge     = document.getElementById("chatBadge");
    if (chatOpen) {
        panel.style.display = "flex";
        requestAnimationFrame(() => { panel.style.opacity = "1"; panel.style.transform = "translateY(0) scale(1)"; });
        if (iconOpen)  iconOpen.style.display  = "none";
        if (iconClose) iconClose.style.display = "block";
        if (badge)     badge.style.display     = "none";
        if (document.getElementById("chatMessages").children.length === 0) renderChatWelcome();
        setTimeout(() => document.getElementById("chatInput").focus(), 200);
    } else {
        panel.style.opacity   = "0";
        panel.style.transform = "translateY(12px) scale(.97)";
        setTimeout(() => { panel.style.display = "none"; }, 250);
        if (iconOpen)  iconOpen.style.display  = "flex";
        if (iconClose) iconClose.style.display = "none";
    }
}

function clearChat() { chatHistory = []; renderChatWelcome(); }

function autoResizeTextarea(el) {
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight, 80) + "px";
}

function handleChatKey(e) {
    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); sendChatMessage(); }
}

function renderContextualQuickReplies(userMsg, botReply) {
    const msg = (userMsg + botReply).toLowerCase();
    let replies;
    if (msg.includes("giá") || msg.includes("rẻ") || msg.includes("budget") || msg.includes("price")) {
        replies = [
            { label: "💰 Dưới 2 triệu",   msg: "Tour nào dưới 2 triệu đồng?" },
            { label: "💎 Tour cao cấp",    msg: "Gợi ý tour cao cấp trên 5 triệu" },
            { label: "📊 So sánh giá",     msg: "So sánh giá các tour biển cho tôi" },
        ];
    } else if (msg.includes("đà lạt") || msg.includes("dalat")) {
        replies = [
            { label: "🌸 Mùa hoa Đà Lạt", msg: "Mùa nào đẹp nhất để đi Đà Lạt?" },
            { label: "⏱ 2 ngày 1 đêm",    msg: "Tour Đà Lạt 2 ngày 1 đêm giá bao nhiêu?" },
            { label: "🏡 Homestay",        msg: "Tour Đà Lạt có homestay đẹp không?" },
        ];
    } else if (msg.includes("biển") || msg.includes("beach") || msg.includes("phú quốc") || msg.includes("nha trang")) {
        replies = [
            { label: "🐠 Phú Quốc",       msg: "Tour Phú Quốc mấy ngày thì đủ?" },
            { label: "🌊 Nha Trang",      msg: "Tour Nha Trang có gì hay?" },
            { label: "🏝 Đà Nẵng",        msg: "Tour Đà Nẵng Hội An combo giá bao nhiêu?" },
        ];
    } else if (msg.includes("gia đình") || msg.includes("trẻ em") || msg.includes("family")) {
        replies = [
            { label: "🎡 Tour vui chơi",  msg: "Tour có khu vui chơi cho trẻ em" },
            { label: "🍜 Tour ẩm thực",   msg: "Tour ẩm thực phù hợp cả gia đình" },
            { label: "🛡 Tour an toàn",   msg: "Tour nào an toàn nhất cho trẻ nhỏ?" },
        ];
    } else if (msg.includes("đặt") || msg.includes("thanh toán") || msg.includes("payment") || msg.includes("book")) {
        replies = [
            { label: "💳 Cách thanh toán", msg: "Có thể thanh toán bằng cách nào?" },
            { label: "❌ Hủy tour",        msg: "Chính sách hủy tour như thế nào?" },
            { label: "🎫 Dùng voucher",    msg: "Cách sử dụng voucher khi đặt tour" },
        ];
    } else {
        replies = [
            { label: "🗺 Xem tất cả tour", msg: "Cho tôi xem danh sách tour đầy đủ" },
            { label: "⭐ Tour hot nhất",   msg: "Tour nào đang được đặt nhiều nhất?" },
            { label: "🎁 Ưu đãi hôm nay", msg: "Có voucher hay khuyến mãi gì không?" },
        ];
    }
    renderQuickReplies(replies);
}

async function sendChatMessage() {
    if (chatIsLoading) return;
    const input = document.getElementById("chatInput");
    const text  = input.value.trim();
    if (!text) return;
    input.value = "";
    input.style.height = "auto";
    document.getElementById("quickReplies").innerHTML = "";
    appendChatMessage("user", text);
    chatHistory.push({ role: "user", content: text });
    if (chatHistory.length > 16) chatHistory = chatHistory.slice(-16);

    chatIsLoading = true;
    document.getElementById("typingIndicator").style.display = "block";
    document.getElementById("chatSendBtn").style.opacity     = "0.4";

    try {
        const response = await fetch("/api/chat", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ systemPrompt: buildSystemPrompt(), messages: chatHistory })
        });
        const data = await response.json();
        document.getElementById("typingIndicator").style.display = "none";
        if (!response.ok) throw new Error(data.message || "API error");
        const reply = data.reply || "";
        if (!reply) throw new Error("Empty response");
        chatHistory.push({ role: "assistant", content: reply });
        appendChatMessage("assistant", reply);
        renderContextualQuickReplies(text, reply);
    } catch (err) {
        document.getElementById("typingIndicator").style.display = "none";
        appendChatMessage("assistant", "⚠️ Xin lỗi, tôi gặp sự cố kết nối. Bạn có thể thử lại hoặc xem trực tiếp danh sách tour nhé!");
        renderQuickReplies([{ label: "🗺 Xem tất cả tour", msg: "Cho tôi xem danh sách tour" }]);
        console.error("Chat error:", err);
    } finally {
        chatIsLoading = false;
        document.getElementById("chatSendBtn").style.opacity = "1";
    }
}

// Show badge after 3s if chat not opened
setTimeout(() => {
    if (!chatOpen) {
        const badge = document.getElementById("chatBadge");
        if (badge) { badge.style.display = "flex"; setTimeout(() => { badge.style.display = "none"; }, 5000); }
    }
}, 3000);