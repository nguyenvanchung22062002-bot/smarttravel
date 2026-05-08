let featuredIndex = 0;

// ── Featured carousel ────────────────────────────────────────────────────────
function slideFeatured(step) {
    if (!allTours.length) return;
    featuredIndex = (featuredIndex + step + allTours.length) % allTours.length;
    renderFeatured();
}

function renderFeatured() {
    if (!allTours.length) return;
    const cards = [0, 1, 2].map((i) => allTours[(featuredIndex + i) % allTours.length]);
    featuredCarousel.innerHTML = cards.map(tourCardHtml).join("");
}

function renderRecommended() {
    const source = wishlist.length ? allTours.filter((x) => wishlist.includes(x.id)) : allTours;
    recommendedGrid.innerHTML = source.slice(0, 4).map(tourCardHtml).join("");
}

// ── Stats ────────────────────────────────────────────────────────────────────
function animateNumber(id, target, decimal = false) {
    let value = 0;
    const step  = Math.max(1, target / 40);
    const el    = document.getElementById(id);
    const timer = setInterval(() => {
        value += step;
        if (value >= target) { value = target; clearInterval(timer); }
        el.textContent = decimal ? Number(value).toFixed(1) : Math.round(value).toLocaleString("en-US");
    }, 24);
}

function animateStats() {
    animateNumber("statUsers", 12500);
    animateNumber("statTours", Math.max(allTours.length, 60));
    animateNumber("statRate",  4.9, true);
}

// ── Points state (in-memory, loaded from API) ────────────────────────────────
let _pointsState = {
    totalPoints:   0,
    currentStreak: 0,
    checkedToday:  false,
    loaded:        false
};

// Cập nhật UI điểm & streak
function syncPointsUI(data) {
    const pointsEl = document.getElementById("pointsValue");
    const streakEl = document.getElementById("streakValue");
    const checkinBtn = document.getElementById("checkinBtn");
    const tierBadge  = document.getElementById("tierBadge");

    if (pointsEl) pointsEl.textContent = (data.totalPoints || 0).toLocaleString("vi-VN");
    if (streakEl) streakEl.textContent = data.currentStreak || 0;

    if (checkinBtn) {
        if (data.checkedToday) {
            checkinBtn.textContent = "✅ Đã check-in hôm nay";
            checkinBtn.disabled = true;
            checkinBtn.classList.add("opacity-60", "cursor-not-allowed");
            checkinBtn.classList.remove("hover:scale-[1.02]");
        } else {
            const nextPts = data.nextCheckinPoints || 20;
            checkinBtn.innerHTML = `<i class="fa-solid fa-calendar-check mr-2"></i>Check in +${nextPts} điểm`;
            checkinBtn.disabled = false;
            checkinBtn.classList.remove("opacity-60", "cursor-not-allowed");
        }
    }

    // Tier badge
    if (tierBadge) {
        const pts = data.totalPoints || 0;
        let tier, tierClass;
        if (pts >= 5000)      { tier = "VIP";    tierClass = "bg-amber-500/30 text-amber-300 border-amber-500/50"; }
        else if (pts >= 2000) { tier = "Gold";   tierClass = "bg-yellow-500/30 text-yellow-300 border-yellow-500/50"; }
        else if (pts >= 500)  { tier = "Silver"; tierClass = "bg-slate-400/30 text-slate-300 border-slate-400/50"; }
        else                  { tier = "Bronze"; tierClass = "bg-orange-800/30 text-orange-300 border-orange-700/50"; }
        tierBadge.textContent = tier;
        tierBadge.className = `rounded-full border px-2.5 py-0.5 text-xs font-semibold ${tierClass}`;
        tierBadge.classList.remove("hidden");
    }
}

// Load điểm từ API (chỉ khi đã login)
async function loadCheckinStatus() {
    if (!getToken()) return;
    try {
        const data = await api("/checkin/me", { requireAuth: true });
        if (data?.success && data.data) {
            _pointsState = { ...data.data, loaded: true };
            syncPointsUI(data.data);
        }
    } catch (e) {
        // Không làm gián đoạn trang nếu API lỗi
        console.warn("loadCheckinStatus error:", e);
    }
}

// ── Daily Check-in ───────────────────────────────────────────────────────────
async function dailyCheckin() {
    if (!getToken()) {
        toast(t("toast.loginFirst") || "Vui lòng đăng nhập!", "error");
        showPage("auth");
        return;
    }

    const btn = document.getElementById("checkinBtn");
    if (btn) { btn.disabled = true; btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin mr-2"></i>Đang xử lý...'; }

    try {
        const data = await api("/checkin", { method: "POST", requireAuth: true });

        if (!data?.success) {
            toast(data?.message || "Check-in thất bại!", "error");
            return;
        }

        const result = data.data;

        // Update local state
        _pointsState.totalPoints   = result.totalPoints;
        _pointsState.currentStreak = result.currentStreak;
        _pointsState.checkedToday  = true;

        syncPointsUI({ ...result, checkedToday: true, nextCheckinPoints: 20 });

        if (result.alreadyChecked) {
            toast("Bạn đã check-in hôm nay rồi! 😊", "info");
        } else {
            toast(result.message || `Check-in thành công! +${result.pointsEarned} điểm 🎉`, "success");

            // Streak milestone animation
            if (result.currentStreak % 7 === 0) {
                setTimeout(() => toast(`🔥 Tuyệt vời! Streak ${result.currentStreak} ngày liên tiếp!`, "success"), 800);
            }
        }

    } catch (e) {
        toast("Có lỗi xảy ra, thử lại sau!", "error");
        console.error("Checkin error:", e);
    } finally {
        // syncPointsUI sẽ update lại button state
        if (_pointsState.checkedToday && btn) {
            btn.textContent = "✅ Đã check-in hôm nay";
            btn.disabled    = true;
        }
    }
}

// ── Points helper (backward-compat với tours.js gọi awardPoints) ─────────────
// Giờ chỉ dùng để update local display — điểm thật đã được award ở backend
// khi booking thành công. Hàm này giữ để không break code cũ.
function getPoints()     { return _pointsState.totalPoints || 0; }
function setPoints(v)    {
    _pointsState.totalPoints = v;
    const el = document.getElementById("pointsValue");
    if (el) el.textContent = Number(v).toLocaleString("vi-VN");
}
function awardPoints(n)  { setPoints(getPoints() + n); }

// ── Vouchers (sẽ được replace hoàn toàn ở Priority #2) ──────────────────────
// Tạm giữ lại hardcode để không break UI ngay bây giờ
function renderVoucherList() {
    const voucherListEl = document.getElementById("voucherList");
    if (!voucherListEl) return;

    const defaults = [
        { code: "SUMMER20",  discount: 20, expiry: "2026-08-30" },
        { code: "WEEKEND10", discount: 10, expiry: "2026-09-15" },
        { code: "NEWUSER15", discount: 15, expiry: "2026-12-31" }
    ];
    // Voucher đã đổi điểm (xem Priority #2 để làm thật)
    const saved = JSON.parse(localStorage.getItem("st_vouchers") || "[]");

    voucherListEl.innerHTML = [...defaults, ...saved].map((v) =>
        `<div class="flex items-center justify-between rounded-xl bg-white/10 p-3 text-sm">
           <div>
             <div class="font-semibold font-mono tracking-wider text-amber-300">${v.code}</div>
             <div class="text-xs text-slate-400 mt-0.5">Hết hạn ${v.expiry}</div>
           </div>
           <span class="rounded-full bg-emerald-500/20 border border-emerald-500/40 px-2.5 py-1 text-xs font-bold text-emerald-300">-${v.discount}%</span>
         </div>`
    ).join("");
}

function redeemPoints() {
    if (getPoints() < 300) return toast(t("toast.needPoints") || "Cần ít nhất 300 điểm!", "error");
    // TODO Priority #2: gọi POST /api/vouchers/redeem
    const code = "POINT10-" + Math.random().toString(36).slice(2, 7).toUpperCase();
    const list  = JSON.parse(localStorage.getItem("st_vouchers") || "[]");
    list.push({ code, discount: 10, expiry: "2026-12-31" });
    localStorage.setItem("st_vouchers", JSON.stringify(list));
    setPoints(getPoints() - 300);
    renderVoucherList();
    toast(`Đã đổi điểm thành công! Mã: ${code}`, "success");
}

function applyVoucher() {
    const voucherInputEl = document.getElementById("voucherInput");
    const code = voucherInputEl?.value.trim().toUpperCase();
    if (!code) return;
    const staticList = [
        { code: "SUMMER20",  discount: 20 },
        { code: "WEEKEND10", discount: 10 },
        { code: "NEWUSER15", discount: 15 }
    ];
    const extra = JSON.parse(localStorage.getItem("st_vouchers") || "[]");
    const found = [...staticList, ...extra].find((x) => x.code === code);
    if (!found) return toast(t("toast.voucherNotFound") || "Mã không hợp lệ!", "error");
    if (typeof activeVoucher !== "undefined") activeVoucher = found.discount;
    toast(`Áp dụng thành công! Giảm ${found.discount}% 🎉`, "success");
}

// ── Countdown (giữ nguyên, sẽ upgrade ở Priority #3 Flash Sale Engine) ───────
window.initCountdown = function () {
    function update() {
        const now      = new Date();
        const midnight = new Date();
        midnight.setHours(24, 0, 0, 0);
        const diff = Math.max(0, midnight - now);
        const pad  = (n) => String(n).padStart(2, "0");
        const el   = (id) => document.getElementById(id);
        if (el("cdDays"))  el("cdDays").textContent  = pad(Math.floor(diff / 86400000));
        if (el("cdHours")) el("cdHours").textContent = pad(Math.floor((diff % 86400000) / 3600000));
        if (el("cdMins"))  el("cdMins").textContent  = pad(Math.floor((diff % 3600000)  / 60000));
        if (el("cdSecs"))  el("cdSecs").textContent  = pad(Math.floor((diff % 60000)    / 1000));
    }
    update();
    setInterval(update, 1000);
};

// ── Load home ─────────────────────────────────────────────────────────────────
async function loadHomeData() {
    if (!allTours.length) {
        featuredCarousel.innerHTML = skeletonCards(3);
        recommendedGrid.innerHTML  = skeletonCards(2);
        const data = await api("/tours", { showLoader: true });
        if (data.success) allTours = data.data || [];
    }
    fillFilters();
    renderFeatured();
    renderRecommended();
    renderVoucherList();
    animateStats();

    // Load trạng thái check-in từ server
    loadCheckinStatus();
}