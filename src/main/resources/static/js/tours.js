const PAGE_SIZE = 9;

let allTours      = [];
let filteredTours = [];
let currentPage   = 1;
let activeVoucher = 0;
let activeVoucherCode = null;
let activeVoucherAmt  = 0;
let activeTourGalleryIndex = 0;
let allTourGrid, pagination;
let tourSearchInput, priceFilter, destinationFilter, ratingFilter, sortFilter;
let heroSearch, heroDestination, heroPrice;
let detailGrid;
let mobileStickyBookBtn;

document.addEventListener("DOMContentLoaded", () => {
    allTourGrid        = document.getElementById("allTourGrid");
    pagination         = document.getElementById("pagination");
    tourSearchInput    = document.getElementById("tourSearchInput");
    priceFilter        = document.getElementById("priceFilter");
    destinationFilter  = document.getElementById("destinationFilter");
    ratingFilter       = document.getElementById("ratingFilter");
    sortFilter         = document.getElementById("sortFilter");
    heroSearch         = document.getElementById("heroSearch");
    heroDestination    = document.getElementById("heroDestination");
    heroPrice          = document.getElementById("heroPrice");
    detailGrid         = document.getElementById("detailGrid");
    mobileStickyBookBtn = document.getElementById("mobileStickyBookBtn");
});

// ── Wishlist ────────────────────────────────────────────────────────────────
let wishlist = JSON.parse(localStorage.getItem("st_wishlist") || "[]");
const isFav = (id) => wishlist.includes(id);

function toggleWishlist(id) {
    wishlist = isFav(id) ? wishlist.filter((x) => x !== id) : [...wishlist, id];
    localStorage.setItem("st_wishlist", JSON.stringify(wishlist));
    renderTours(filteredTours.length ? filteredTours : allTours);
    if (typeof renderRecommended === "function") renderRecommended();
}

// ── Tour card ───────────────────────────────────────────────────────────────
function tourCardHtml(tour) {
    if (!tour || typeof tour.id === "undefined") return "";
    const images = getTourImages(tour);
    const coverImage = images[0] || "https://images.unsplash.com/photo-1469474968028-56623f02e42e?auto=format&fit=crop&w=1200&q=80";
    const safeName = escapeHtml(tour.name || "");
    const safeDestination = escapeHtml(tour.destination || "");
    return `
    <article class="tour-card glass rounded-2xl p-3 transition-all duration-300 hover:scale-[1.02]">
      <div class="relative h-44 cursor-pointer overflow-hidden rounded-xl" onclick="showTourDetail(${tour.id})">
        <img src="${coverImage}" alt="${safeName}" class="h-full w-full object-cover transition-transform duration-500 hover:scale-110" loading="lazy" />
        <div class="absolute inset-0 bg-gradient-to-t from-slate-900/80 to-transparent"></div>
        <button class="absolute right-3 top-3 h-8 w-8 rounded-full bg-black/35 text-sm transition-transform hover:scale-110"
          onclick="event.stopPropagation();toggleWishlist(${tour.id})">${isFav(tour.id) ? "❤️" : "🤍"}</button>
        ${tour.badge ? `<span class="absolute left-3 top-3 rounded-full bg-rose-500/80 px-2 py-0.5 text-xs font-semibold">${escapeHtml(tour.badge)}</span>` : ""}
        <div class="absolute bottom-3 left-3 text-sm">${starHtml(tour.avgRating || 5)} <span class="text-white/80">${tour.avgRating || "5.0"}</span></div>
      </div>
      <h4 class="mt-3 text-base font-bold leading-snug line-clamp-2">${safeName}</h4>
      <p class="mt-1 text-xs text-slate-400"><i class="fa-solid fa-location-dot mr-1 text-cyan-400"></i>${safeDestination}</p>
      <p class="text-xs text-slate-400 mt-0.5"><i class="fa-solid fa-clock mr-1 text-emerald-400"></i>${tour.durationDays || 0}N${tour.durationNights || 0}Đ</p>
      <div class="mt-3 flex items-end justify-between">
        <p class="text-xl font-black text-cyan-300">${fmtPrice(tour.price)}</p>
        <span class="rounded-full bg-white/10 px-3 py-1 text-xs">${tour.availableSlots || 0} chỗ</span>
      </div>
      <div class="mt-2">
        <button type="button" class="w-full rounded-xl bg-gradient-to-r from-emerald-400 to-cyan-400 py-2 font-semibold text-slate-900 transition hover:opacity-90"
          onclick="showTourDetail(${tour.id})">Xem chi tiết & Đặt tour</button>
      </div>
    </article>`;
}

// ── Fill filter dropdowns ───────────────────────────────────────────────────
function fillFilters() {
    const destinations = [...new Set(allTours.map((x) => x.destination).filter(Boolean))];
    const destOpts = `<option value="">Tất cả điểm đến</option>` +
        destinations.map((d) => `<option value="${escapeHtml(d)}">${escapeHtml(d)}</option>`).join("");
    if (destinationFilter) destinationFilter.innerHTML = destOpts;
    if (heroDestination)   heroDestination.innerHTML   = destOpts;

    const priceOpts = `<option value="">Mức giá</option>
    <option value="0-2000000">Dưới 2 triệu</option>
    <option value="2000000-5000000">2 - 5 triệu</option>
    <option value="5000000-999999999">Trên 5 triệu</option>`;
    if (priceFilter) priceFilter.innerHTML = priceOpts;
    if (heroPrice)   heroPrice.innerHTML   = priceOpts;

    if (ratingFilter) ratingFilter.innerHTML = `<option value="">Đánh giá</option>
    <option value="4">≥ 4 sao</option><option value="4.5">≥ 4.5 sao</option><option value="5">5 sao</option>`;

    if (sortFilter) sortFilter.innerHTML = `<option value="">Sắp xếp</option>
    <option value="price-asc">Giá tăng dần</option>
    <option value="price-desc">Giá giảm dần</option>
    <option value="rating">Đánh giá cao</option>
    <option value="popularity">Phổ biến nhất</option>`;
}

// ── Pagination ──────────────────────────────────────────────────────────────
function renderPagination(totalPages) {
    if (!pagination) return;
    pagination.innerHTML = new Array(totalPages).fill(0).map((_, i) => {
        const page = i + 1;
        return `<button class="${page === currentPage ? "bg-cyan-400 text-slate-900" : "glass"} h-9 w-9 rounded-lg text-sm transition"
      onclick="goToPage(${page})">${page}</button>`;
    }).join("");
}

function renderTours(list) {
    const grid = allTourGrid || document.getElementById("allTourGrid");
    if (!grid) return;
    if (!list || !list.length) {
        grid.innerHTML = `<div class="glass col-span-full rounded-2xl p-12 text-center text-slate-400">Không tìm thấy tour nào phù hợp.</div>`;
        if (pagination) pagination.innerHTML = "";
        return;
    }
    const totalPages = Math.ceil(list.length / PAGE_SIZE);
    currentPage = Math.min(Math.max(1, currentPage), totalPages);
    const start = (currentPage - 1) * PAGE_SIZE;
    grid.innerHTML = list.slice(start, start + PAGE_SIZE).map(tourCardHtml).join("");
    renderPagination(totalPages);
}

function goToPage(page) { currentPage = page; renderTours(filteredTours.length ? filteredTours : allTours); }
function changePage(step) {
    const list = filteredTours.length ? filteredTours : allTours;
    if (!list || !list.length) return;
    currentPage = Math.min(Math.ceil(list.length / PAGE_SIZE), Math.max(1, currentPage + step));
    renderTours(list);
}

// ── Load tours ──────────────────────────────────────────────────────────────
async function loadAllTours() {
    const gridEl = allTourGrid || document.getElementById("allTourGrid");
    if (!gridEl) return;
    if (!allTours || !allTours.length) {
        gridEl.innerHTML = skeletonCards(9);
        const data = await api("/tours", { showLoader: true });
        if (!data.success) {
            gridEl.innerHTML = `<div class="glass col-span-full rounded-2xl p-12 text-center">Không thể tải danh sách tour.</div>`;
            return;
        }
        allTours = data.data || [];
    }
    fillFilters();
    filteredTours = [...allTours];
    currentPage = 1;
    renderTours(filteredTours);
}

// ── Filter ──────────────────────────────────────────────────────────────────
function filterTours() {
    const q = (tourSearchInput?.value || "").toLowerCase();
    const p = priceFilter?.value || "";
    const d = destinationFilter?.value || "";
    const r = ratingFilter?.value || "";
    const s = sortFilter?.value || "";
    if (!allTours || !allTours.length) return;

    filteredTours = allTours.filter((tour) => {
        if (!tour) return false;
        const textMatch = !q || (tour.name || "").toLowerCase().includes(q)
            || (tour.destination || "").toLowerCase().includes(q)
            || (tour.description || "").toLowerCase().includes(q);
        let priceMatch = true;
        if (p) {
            const parts = p.split("-").map(Number);
            priceMatch = tour.price >= (parts[0] || 0) && tour.price <= (parts[1] || Infinity);
        }
        return textMatch && priceMatch
            && (!d || tour.destination === d)
            && (!r || Number(tour.avgRating || 0) >= Number(r));
    });

    if (s === "price-asc")  filteredTours.sort((a, b) => (a.price || 0) - (b.price || 0));
    if (s === "price-desc") filteredTours.sort((a, b) => (b.price || 0) - (a.price || 0));
    if (s === "rating")     filteredTours.sort((a, b) => (b.avgRating || 0) - (a.avgRating || 0));
    if (s === "popularity") filteredTours.sort((a, b) => (b.totalReviews || 0) - (a.totalReviews || 0));
    currentPage = 1;
    renderTours(filteredTours);
}

function applyHeroSearch() {
    showPage("tours");
    setTimeout(() => {
        if (tourSearchInput)   tourSearchInput.value   = heroSearch?.value || "";
        if (destinationFilter) destinationFilter.value = heroDestination?.value || "";
        if (priceFilter)       priceFilter.value       = heroPrice?.value || "";
        filterTours();
    }, 120);
}

// ── Gallery ─────────────────────────────────────────────────────────────────
function setActiveTourImage(index) {
    const preview = document.getElementById("tourGalleryPreview");
    if (!preview?.dataset?.images) return;
    try {
        const images = JSON.parse(preview.dataset.images);
        if (!images?.length) return;
        activeTourGalleryIndex = (index + images.length) % images.length;
        preview.src = images[activeTourGalleryIndex];
        document.querySelectorAll(".gallery-thumb").forEach((thumb, i) =>
            thumb.classList.toggle("active", i === activeTourGalleryIndex));
    } catch (e) { console.error("Gallery error:", e); }
}
function slideTourGallery(step) { setActiveTourImage(activeTourGalleryIndex + step); }

// ── Voucher (trong trang đặt tour) ──────────────────────────────────────────
async function applyTourVoucher(basePrice) {
    const inputEl = document.getElementById("tourVoucherInput");
    const msgEl   = document.getElementById("voucherMsg");
    const badgeEl = document.getElementById("voucherBadge");
    const code    = inputEl?.value.trim().toUpperCase();

    if (!code) return;

    // Reset state
    activeVoucher     = 0;
    activeVoucherCode = null;
    activeVoucherAmt  = 0;

    if (msgEl) { msgEl.textContent = "Đang kiểm tra..."; msgEl.className = "mt-1 text-xs text-slate-400"; }

    const adults   = Number(document.getElementById("bkAdults")?.value)   || 1;
    const children = Number(document.getElementById("bkChildren")?.value) || 0;
    const subtotal = basePrice * adults + Math.round(basePrice * 0.7 * children);

    try {
        const data = await api("/vouchers/validate", {
            method:      "POST",
            requireAuth: false,
            body:        JSON.stringify({ code, originalPrice: subtotal })
        });

        if (!data?.success) {
            if (msgEl) { msgEl.textContent = data?.message || "Mã không hợp lệ!"; msgEl.className = "mt-1 text-xs text-rose-400"; }
            if (badgeEl) badgeEl.classList.add("hidden");
            renderPriceBreakdown(basePrice);
            return;
        }

        const result = data.data;
        activeVoucherCode = result.code;
        activeVoucherAmt  = result.discountAmount;
        activeVoucher     = subtotal > 0
            ? Math.round((result.discountAmount / subtotal) * 100)
            : Number(result.discountValue);

        if (msgEl) {
            msgEl.textContent = `✅ Giảm ${fmtPrice(result.discountAmount)} (hết hạn ${result.expiryDate})`;
            msgEl.className   = "mt-1 text-xs text-emerald-400";
        }
        if (badgeEl) {
            badgeEl.textContent = `-${activeVoucher}%`;
            badgeEl.classList.remove("hidden");
        }
        renderPriceBreakdown(basePrice);

    } catch (err) {
        if (msgEl) { msgEl.textContent = "Lỗi kiểm tra voucher!"; msgEl.className = "mt-1 text-xs text-rose-400"; }
        console.error("Voucher validate error:", err);
    }
}

function removeTourVoucher(basePrice) {
    activeVoucher = null;
    const input   = document.getElementById("tourVoucherInput");
    const msgEl   = document.getElementById("voucherMsg");
    const badgeEl = document.getElementById("voucherBadge");
    if (input)   input.value = "";
    if (msgEl)   { msgEl.textContent = ""; }
    if (badgeEl) badgeEl.classList.add("hidden");
    renderPriceBreakdown(basePrice);
    toast("Đã xóa mã giảm giá", "info");
}

// ── Tour Detail ──────────────────────────────────────────────────────────────
async function loadTourDetail(id) {
    // Lấy lại element mỗi lần (tránh null nếu page chưa hiển thị)
    detailGrid = document.getElementById("detailGrid");
    if (!detailGrid) { console.error("detailGrid not found"); return; }
    if (!id || typeof id !== "number") { console.error("Invalid tour ID:", id); return; }

    // Chuyển trang trước để DOM sẵn sàng
    showPage("detail");

    // Xóa reviews cũ
    const reviewsSection = document.getElementById("tourReviewsSection");
    if (reviewsSection) reviewsSection.innerHTML = "";

    // Tìm trong cache
    let tour = allTours.find((x) => x?.id === id);
    if (!tour) {
        detailGrid.innerHTML = skeletonCards(2);
        const data = await api(`/tours/${id}`);
        if (!data.success || !data.data) {
            detailGrid.innerHTML = `<div class="glass rounded-2xl p-12 text-center text-slate-400">Không tìm thấy tour.</div>`;
            return;
        }
        tour = data.data;
        allTours.push(tour);
    }

    const images      = getTourImages(tour);
    const primaryImage = images?.[0] || "";
    activeTourGalleryIndex = 0;
    // Reset voucher mỗi lần vào tour mới
    activeVoucher = null;

    const safeName        = escapeHtml(tour.name || "");
    const safeDescription = escapeHtml(tour.description || "");
    const safeDestination = escapeHtml(tour.destination || "");
    const rawLongDesc     = tour.descriptionLong || tour.description || "";
    const safeLongDesc    = rawLongDesc; // formatItinerary tự xử lý escape bên trong

    detailGrid.innerHTML = `
    <!-- LEFT: Info -->
    <div class="glass rounded-3xl p-6 space-y-6">
      <!-- Gallery -->
      <div class="relative overflow-hidden rounded-2xl border border-white/10">
        ${primaryImage
        ? `<img id="tourGalleryPreview" data-images='${JSON.stringify(images).replace(/'/g,"&#39;")}' src="${primaryImage}"
               alt="${safeName}" class="h-72 w-full object-cover md:h-80 transition-all duration-500" />`
        : `<div class="flex h-72 w-full items-center justify-center bg-white/5 text-sm text-slate-400">Chưa có ảnh</div>`}
        ${images?.length > 1 ? `
          <button class="absolute left-3 top-1/2 -translate-y-1/2 rounded-full bg-slate-900/60 px-3 py-2 text-xs backdrop-blur-sm hover:bg-slate-900/80" onclick="slideTourGallery(-1)"><i class="fa-solid fa-chevron-left"></i></button>
          <button class="absolute right-3 top-1/2 -translate-y-1/2 rounded-full bg-slate-900/60 px-3 py-2 text-xs backdrop-blur-sm hover:bg-slate-900/80" onclick="slideTourGallery(1)"><i class="fa-solid fa-chevron-right"></i></button>
          <div class="absolute bottom-3 right-3 rounded-full bg-black/40 px-2 py-1 text-xs backdrop-blur-sm">
            <span id="galleryCounter">1</span>/${images.length}
          </div>` : ""}
      </div>
      ${images?.length > 1 ? `
        <div class="grid grid-cols-5 gap-2">
          ${images.slice(0,5).map((img, idx) => `
            <button class="gallery-thumb ${idx===0?"active ring-2 ring-cyan-400":""} overflow-hidden rounded-xl border border-white/10 transition"
              onclick="setActiveTourImage(${idx})">
              <img src="${img}" alt="thumb-${idx}" class="h-14 w-full object-cover" loading="lazy" />
            </button>`).join("")}
        </div>` : ""}

      <!-- Info -->
      <div>
        <h2 class="text-3xl font-black">${safeName}</h2>
        <div class="mt-3 flex flex-wrap gap-2 text-sm">
          <span class="flex items-center gap-1 rounded-full bg-cyan-500/15 border border-cyan-500/30 px-3 py-1 text-cyan-300">
            <i class="fa-solid fa-location-dot"></i> ${safeDestination}</span>
          <span class="flex items-center gap-1 rounded-full bg-white/10 px-3 py-1">
            <i class="fa-solid fa-clock text-emerald-400"></i> ${tour.durationDays || 0} ngày ${tour.durationNights || 0} đêm</span>
          <span class="flex items-center gap-1 rounded-full bg-white/10 px-3 py-1">
            ${starHtml(tour.avgRating || 5)} <span class="ml-1">${tour.avgRating || "5.0"}</span>
            <span class="text-slate-400">(${tour.totalReviews || 0} đánh giá)</span></span>
          <span class="flex items-center gap-1 rounded-full bg-emerald-500/15 border border-emerald-500/30 px-3 py-1 text-emerald-300">
            <i class="fa-solid fa-users"></i> Còn ${tour.availableSlots || 0} chỗ</span>
        </div>
        ${safeDescription ? `<p class="mt-4 leading-relaxed text-slate-300">${safeDescription}</p>` : ""}
      </div>

      ${safeLongDesc ? `
      <section class="rounded-2xl overflow-hidden" style="border:1px solid rgba(34,211,238,0.15);background:linear-gradient(135deg,rgba(14,116,144,0.08) 0%,rgba(15,23,42,0.6) 100%);">
        <!-- Header -->
        <div style="padding:16px 20px 14px;border-bottom:1px solid rgba(34,211,238,0.12);background:linear-gradient(90deg,rgba(14,116,144,0.18),transparent);display:flex;align-items:center;gap:10px;">
          <span style="width:32px;height:32px;border-radius:10px;background:linear-gradient(135deg,#0e7490,#0891b2);display:inline-flex;align-items:center;justify-content:center;flex-shrink:0;">
            <i class="fa-solid fa-map-location-dot" style="color:#e0f7fa;font-size:13px;"></i>
          </span>
          <h3 style="margin:0;font-size:15px;font-weight:700;color:#67e8f9;letter-spacing:0.3px;">Lịch trình chi tiết</h3>
        </div>
        <!-- Content -->
        <div class="itinerary-content" style="padding:18px 20px 20px;">${formatItinerary(safeLongDesc)}</div>
      </section>` : ""}
    </div>

    <!-- RIGHT: Booking panel -->
    <div id="bookingPanel" class="space-y-4">
      <!-- Price card -->
      <div class="glass rounded-3xl p-6">
        <div class="flex items-baseline justify-between">
          <div>
            <span class="text-sm text-slate-400">Từ</span>
            <span class="ml-2 text-3xl font-black text-cyan-300">${fmtPrice(tour.price)}</span>
          </div>
          <span class="text-sm text-slate-400">/ người lớn</span>
        </div>
        <div class="mt-1 text-xs text-slate-500">Trẻ em (dưới 12): 70% giá người lớn • Phí dịch vụ: 5%</div>
      </div>

      <!-- Booking form -->
      <div class="glass rounded-3xl p-6 space-y-4">
        <h3 class="text-lg font-bold flex items-center gap-2">
          <i class="fa-solid fa-calendar-check text-cyan-400"></i> Thông tin đặt tour
        </h3>

        <!-- Date -->
        <div>
          <label class="text-sm font-medium text-slate-300 mb-1 block">Ngày khởi hành <span class="text-rose-400">*</span></label>
          <input id="bkDate" type="date" min="${new Date().toISOString().slice(0,10)}"
            class="w-full rounded-xl border border-white/10 bg-white/10 px-3 py-2.5 text-sm focus:border-cyan-400 focus:outline-none transition"
            oninput="renderPriceBreakdown(${tour.price})" />
        </div>

        <!-- Adults + Children -->
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-sm font-medium text-slate-300 mb-1 block">Người lớn</label>
            <div class="flex items-center gap-2">
              <button onclick="adjustCount('bkAdults',-1,1,${tour.price})" class="h-9 w-9 rounded-lg bg-white/10 font-bold hover:bg-white/20">−</button>
              <input id="bkAdults" type="number" min="1" value="1" class="w-12 rounded-lg border border-white/10 bg-white/10 px-2 py-1.5 text-center text-sm"
                oninput="renderPriceBreakdown(${tour.price})" />
              <button onclick="adjustCount('bkAdults',1,1,${tour.price})" class="h-9 w-9 rounded-lg bg-white/10 font-bold hover:bg-white/20">+</button>
            </div>
          </div>
          <div>
            <label class="text-sm font-medium text-slate-300 mb-1 block">Trẻ em</label>
            <div class="flex items-center gap-2">
              <button onclick="adjustCount('bkChildren',-1,0,${tour.price})" class="h-9 w-9 rounded-lg bg-white/10 font-bold hover:bg-white/20">−</button>
              <input id="bkChildren" type="number" min="0" value="0" class="w-12 rounded-lg border border-white/10 bg-white/10 px-2 py-1.5 text-center text-sm"
                oninput="renderPriceBreakdown(${tour.price})" />
              <button onclick="adjustCount('bkChildren',1,0,${tour.price})" class="h-9 w-9 rounded-lg bg-white/10 font-bold hover:bg-white/20">+</button>
            </div>
          </div>
        </div>

        <!-- Pickup -->
        <div>
          <label class="text-sm font-medium text-slate-300 mb-1 block">Điểm đón</label>
          <input id="bkPickup" class="w-full rounded-xl border border-white/10 bg-white/10 px-3 py-2.5 text-sm focus:border-cyan-400 focus:outline-none transition"
            placeholder="Nhập địa chỉ đón..." />
        </div>

        <!-- Note -->
        <div>
          <label class="text-sm font-medium text-slate-300 mb-1 block">Ghi chú</label>
          <textarea id="bkNote" rows="2" class="w-full rounded-xl border border-white/10 bg-white/10 px-3 py-2.5 text-sm focus:border-cyan-400 focus:outline-none transition resize-none"
            placeholder="Yêu cầu đặc biệt, dị ứng thực phẩm..."></textarea>
        </div>

        <!-- Payment method -->
        <div>
          <label class="text-sm font-medium text-slate-300 mb-2 block">Phương thức thanh toán</label>
          <div class="grid grid-cols-2 gap-2">
            ${[
        { val: "CASH",          icon: "fa-money-bill-wave",    label: "Tiền mặt",      color: "emerald" },
        { val: "BANK_TRANSFER", icon: "fa-building-columns",   label: "Chuyển khoản",  color: "blue" },
        { val: "VNPAY",         icon: "fa-credit-card",        label: "VNPAY",         color: "red" },
        { val: "MOMO",          icon: "fa-mobile-screen",      label: "MoMo",          color: "pink" }
    ].map(m => `
            <label class="payment-option flex cursor-pointer items-center gap-2 rounded-xl border border-white/10 bg-white/5 px-3 py-2.5 text-sm transition hover:border-${m.color}-400/50 hover:bg-${m.color}-500/10">
              <input type="radio" name="paymentMethod" value="${m.val}" class="hidden" onchange="document.querySelectorAll('.payment-option').forEach(x=>x.classList.remove('selected-payment'));this.closest('label').classList.add('selected-payment')" ${m.val==="CASH"?"checked":""}>
              <i class="fa-solid ${m.icon} text-${m.color}-400 w-4"></i>
              <span class="font-medium">${m.label}</span>
            </label>`).join("")}
          </div>
          <style>.selected-payment{border-color:rgba(34,211,238,.6)!important;background:rgba(34,211,238,.08)!important;}</style>
          <script>document.querySelector('.payment-option input[value="CASH"]')?.closest('label')?.classList.add('selected-payment');<\/script>
        </div>

        <!-- Voucher -->
        <div class="rounded-xl border border-dashed border-white/20 bg-white/5 p-4">
          <div class="flex items-center justify-between mb-2">
            <label class="text-sm font-medium text-slate-300 flex items-center gap-2">
              <i class="fa-solid fa-ticket text-amber-400"></i> Mã giảm giá
            </label>
            <span id="voucherBadge" class="hidden rounded-full bg-emerald-500/20 border border-emerald-500/40 px-2 py-0.5 text-xs font-bold text-emerald-300"></span>
          </div>
          <div class="flex gap-2">
            <input id="tourVoucherInput" class="flex-1 rounded-xl border border-white/10 bg-white/10 px-3 py-2 text-sm uppercase placeholder:normal-case placeholder:text-slate-500 focus:border-amber-400 focus:outline-none transition"
              placeholder="Nhập mã voucher..." maxlength="20"
              onkeydown="if(event.key==='Enter') applyTourVoucher(${tour.price})" />
            <button onclick="applyTourVoucher(${tour.price})" class="rounded-xl bg-amber-400 px-4 py-2 text-sm font-bold text-slate-900 transition hover:bg-amber-300">Áp dụng</button>
          </div>
          <p id="voucherMsg" class="mt-1 text-xs"></p>
          <div class="mt-2 flex flex-wrap gap-1">
            ${["SUMMER20","WEEKEND10","NEWUSER15"].map(c =>
        `<button onclick="document.getElementById('tourVoucherInput').value='${c}';applyTourVoucher(${tour.price})"
                class="rounded-lg bg-white/10 px-2 py-1 text-xs text-slate-400 hover:bg-white/20 hover:text-amber-300 transition">${c}</button>`
    ).join("")}
          </div>
        </div>

        <!-- Price breakdown -->
        <div class="rounded-2xl bg-gradient-to-br from-slate-800/80 to-slate-900/80 border border-white/10 p-4 space-y-2">
          <h4 class="text-sm font-semibold text-slate-300 mb-3 flex items-center gap-2">
            <i class="fa-solid fa-receipt text-cyan-400"></i> Chi tiết giá
          </h4>
          <div id="priceBreakdown" class="space-y-1.5 text-sm"></div>
          <div class="border-t border-white/10 pt-3 mt-3">
            <div class="flex justify-between items-center">
              <span class="font-semibold text-slate-200">Tổng thanh toán</span>
              <span id="totalPriceDisplay" class="text-2xl font-black text-cyan-300">${fmtPrice(tour.price)}</span>
            </div>
            <div id="discountRow" class="hidden flex justify-between text-xs text-emerald-400 mt-1">
              <span><i class="fa-solid fa-tag mr-1"></i>Đã giảm giá</span>
              <span id="discountDisplay" class="font-semibold"></span>
            </div>
          </div>
        </div>

        <!-- Book button -->
        <button id="bookNowBtn" type="button"
          class="w-full rounded-2xl bg-gradient-to-r from-emerald-400 to-cyan-400 py-4 font-black text-slate-900 text-lg shadow-lg shadow-cyan-500/25 transition hover:scale-[1.02] hover:shadow-cyan-500/40 active:scale-[0.98]"
          onclick="submitBooking(${tour.id}, ${tour.price}, event)">
          <i class="fa-solid fa-calendar-check mr-2"></i>Đặt tour ngay
        </button>
        <p class="text-center text-xs text-slate-500">
          <i class="fa-solid fa-shield-halved mr-1 text-emerald-400"></i>Bảo mật thanh toán · Hoàn tiền 100% nếu hủy trước 48h
        </p>
      </div>
    </div>`;

    // Load reviews cho tour này
    loadTourReviews(id);

    mobileStickyBookBtn = mobileStickyBookBtn || document.getElementById("mobileStickyBookBtn");
    mobileStickyBookBtn?.classList.remove("hidden");
    renderPriceBreakdown(tour.price);

    // Khởi tạo trạng thái selected cho radio payment
    setTimeout(() => {
        document.querySelector('.payment-option input[value="CASH"]')?.closest('label')?.classList.add('selected-payment');
    }, 50);
}

// Navigation helper: show tour detail from Tours page
function showTourDetail(tourId) {
    const t = allTours.find((x) => x?.id === tourId);
    if (t) {
        renderTourDetail(t);
    } else {
        if (typeof api === 'function') {
            api("/tours/" + tourId, { showLoader: true }).then((resp) => {
                if (resp && resp.success && resp.data) {
                    allTours.push(resp.data);
                    renderTourDetail(resp.data);
                } else {
                    console.error("Tour not found:", tourId);
                }
            }).catch((err) => console.error("Fetch tour failed:", err));
        }
    }
}

function renderTourDetail(tour) {
    if (!tour) return;
    // Use existing detailed loader to render the tour in the UI
    loadTourDetail(tour.id);
}

// ── Load reviews của tour ───────────────────────────────────────────────────
async function loadTourReviews(tourId) {
    const section = document.getElementById("tourReviewsSection");
    if (!section) return;

    section.innerHTML = `
    <div class="glass rounded-3xl p-6">
      <h3 class="text-xl font-bold mb-4 flex items-center gap-2">
        <i class="fa-solid fa-star text-amber-400"></i> Đánh giá của khách hàng
      </h3>
      <div class="flex items-center justify-center py-8">
        <i class="fa-solid fa-spinner fa-spin text-cyan-400 text-2xl"></i>
      </div>
    </div>`;

    const data = await api(`/reviews?tourId=${tourId}`);
    const reviews = (data.success && data.data) ? data.data : [];

    if (!reviews.length) {
        section.innerHTML = `
        <div class="glass rounded-3xl p-6">
          <h3 class="text-xl font-bold mb-4 flex items-center gap-2">
            <i class="fa-solid fa-star text-amber-400"></i> Đánh giá của khách hàng
          </h3>
          <div class="flex flex-col items-center py-10 text-slate-400">
            <i class="fa-regular fa-comment-dots text-4xl mb-3"></i>
            <p>Chưa có đánh giá nào cho tour này.</p>
            <p class="text-sm mt-1">Hãy là người đầu tiên chia sẻ trải nghiệm!</p>
          </div>
        </div>`;
        return;
    }

    // Tính trung bình
    const avgRating = (reviews.reduce((s, r) => s + (r.rating || 0), 0) / reviews.length).toFixed(1);
    const dist = [5,4,3,2,1].map(star => ({
        star,
        count: reviews.filter(r => Math.round(r.rating) === star).length,
        pct: Math.round(reviews.filter(r => Math.round(r.rating) === star).length / reviews.length * 100)
    }));

    section.innerHTML = `
    <div class="glass rounded-3xl p-6">
      <h3 class="text-xl font-bold mb-6 flex items-center gap-2">
        <i class="fa-solid fa-star text-amber-400"></i> Đánh giá của khách hàng
        <span class="ml-2 rounded-full bg-amber-400/20 px-3 py-0.5 text-sm font-semibold text-amber-300">${reviews.length} đánh giá</span>
      </h3>

      <!-- Summary -->
      <div class="mb-6 flex flex-col sm:flex-row gap-6 items-start sm:items-center p-4 rounded-2xl bg-white/5 border border-white/10">
        <div class="text-center flex-shrink-0">
          <div class="text-5xl font-black text-amber-300">${avgRating}</div>
          <div class="mt-1">${starHtml(Math.round(avgRating))}</div>
          <div class="mt-1 text-xs text-slate-400">${reviews.length} đánh giá</div>
        </div>
        <div class="flex-1 w-full space-y-1.5">
          ${dist.map(d => `
          <div class="flex items-center gap-2 text-xs">
            <span class="w-6 text-right text-amber-300 font-semibold">${d.star}★</span>
            <div class="flex-1 h-2 rounded-full bg-white/10 overflow-hidden">
              <div class="h-full rounded-full bg-gradient-to-r from-amber-400 to-orange-400 transition-all duration-700" style="width:${d.pct}%"></div>
            </div>
            <span class="w-6 text-slate-400">${d.count}</span>
          </div>`).join("")}
        </div>
      </div>

      <!-- Review cards -->
      <div class="grid gap-4 md:grid-cols-2">
        ${reviews.slice(0, 6).map(review => `
        <div class="rounded-2xl bg-white/5 border border-white/10 p-4 hover:border-white/20 transition">
          <div class="flex items-start gap-3">
            <img src="https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(review.userName || 'U')}&backgroundColor=22d3ee,10b981,a855f7"
              class="h-10 w-10 rounded-full flex-shrink-0 ring-2 ring-white/10" alt="${escapeHtml(review.userName || '')}" />
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between gap-2">
                <span class="font-semibold text-sm truncate">${escapeHtml(review.userName || "Khách hàng")}</span>
                <span class="text-xs text-slate-500 flex-shrink-0">${review.createdAt ? new Date(review.createdAt).toLocaleDateString("vi-VN") : ""}</span>
              </div>
              <div class="mt-0.5 text-sm text-amber-400">${starHtml(review.rating || 5)}</div>
            </div>
          </div>
          ${review.title ? `<p class="mt-2 text-sm font-semibold text-cyan-200">${escapeHtml(review.title)}</p>` : ""}
          <p class="mt-1.5 text-sm text-slate-300 leading-relaxed line-clamp-3">${escapeHtml(review.body || review.content || "")}</p>
        </div>`).join("")}
      </div>
      ${reviews.length > 6 ? `<p class="mt-4 text-center text-sm text-slate-400">Và ${reviews.length - 6} đánh giá khác...</p>` : ""}
    </div>`;
}

// ── Helpers: adjust count với +/- buttons ───────────────────────────────────
function adjustCount(id, delta, min, basePrice) {
    const el = document.getElementById(id);
    if (!el) return;
    el.value = Math.max(min, Number(el.value) + delta);
    renderPriceBreakdown(basePrice);
}

// ── Price breakdown ─────────────────────────────────────────────────────────
function calcEstimatedPrice(basePrice) {
    const adults   = Number(document.getElementById("bkAdults")?.value) || 1;
    const children = Number(document.getElementById("bkChildren")?.value) || 0;
    if (!basePrice || typeof basePrice !== "number") return 0;
    const subtotal = basePrice * adults + basePrice * 0.7 * children;
    let total = Math.round(subtotal * 1.05);
    if (activeVoucher) total = Math.round(total * (1 - activeVoucher / 100));
    return total;
}

function renderPriceBreakdown(basePrice) {
    if (!basePrice || typeof basePrice !== "number") return;
    const adults   = Number(document.getElementById("bkAdults")?.value) || 1;
    const children = Number(document.getElementById("bkChildren")?.value) || 0;

    const adultTotal  = basePrice * adults;
    const childTotal  = Math.round(basePrice * 0.7 * children);
    const subtotal    = adultTotal + childTotal;
    const vatAmount   = Math.round(subtotal * 0.05);
    const totalWithVat = subtotal + vatAmount;

    let finalTotal = totalWithVat;
    let discountAmt = 0;
    if (activeVoucher) {
        discountAmt = Math.round(totalWithVat * activeVoucher / 100);
        finalTotal  = totalWithVat - discountAmt;
    }

    const breakdownEl = document.getElementById("priceBreakdown");
    if (breakdownEl) {
        breakdownEl.innerHTML = `
        <div class="flex justify-between text-slate-300">
          <span>${adults} người lớn × ${fmtPrice(basePrice)}</span>
          <span>${fmtPrice(adultTotal)}</span>
        </div>
        ${children > 0 ? `
        <div class="flex justify-between text-slate-300">
          <span>${children} trẻ em × ${fmtPrice(Math.round(basePrice * 0.7))}</span>
          <span>${fmtPrice(childTotal)}</span>
        </div>` : ""}
        <div class="flex justify-between text-slate-400 text-xs">
          <span>Phí dịch vụ (5%)</span>
          <span>+${fmtPrice(vatAmount)}</span>
        </div>
        ${activeVoucher ? `
        <div class="flex justify-between text-emerald-400 text-xs">
          <span><i class="fa-solid fa-tag mr-1"></i>Mã giảm giá (${activeVoucher}%)</span>
          <span class="font-semibold">-${fmtPrice(discountAmt)}</span>
        </div>` : ""}`;
    }

    const totalDisplay = document.getElementById("totalPriceDisplay");
    if (totalDisplay) totalDisplay.textContent = fmtPrice(finalTotal);

    const discountRow = document.getElementById("discountRow");
    const discountDisplay = document.getElementById("discountDisplay");
    if (discountRow) discountRow.classList.toggle("hidden", !activeVoucher);
    if (discountDisplay && activeVoucher) discountDisplay.textContent = `-${fmtPrice(discountAmt)}`;
}

const scrollToBook = () => document.getElementById("bookingPanel")?.scrollIntoView({ behavior: "smooth" });

// ── Submit booking ──────────────────────────────────────────────────────────
async function submitBooking(tourId, basePrice, evt) {
    if (evt && typeof evt.preventDefault === "function") evt.preventDefault();

    if (!getToken()) {
        toast(t("toast.loginFirst") || "Vui lòng đăng nhập để đặt tour!", "error");
        showPage("auth");
        return;
    }
    if (!tourId) return toast("Lỗi: không xác định được tour!", "error");

    const bkDateEl     = document.getElementById("bkDate");
    const bkAdultsEl   = document.getElementById("bkAdults");
    const bkChildrenEl = document.getElementById("bkChildren");
    const bkPickupEl   = document.getElementById("bkPickup");
    const bkNoteEl     = document.getElementById("bkNote");
    const bookNowBtn   = document.getElementById("bookNowBtn");
    const paymentEl    = document.querySelector('input[name="paymentMethod"]:checked');

    if (!bkDateEl?.value) return toast("Vui lòng chọn ngày khởi hành!", "error");
    const adults   = Number(bkAdultsEl?.value)   || 1;
    const children = Number(bkChildrenEl?.value) || 0;
    if (adults < 1)       return toast("Phải có ít nhất 1 người lớn!", "error");
    if (!paymentEl?.value) return toast("Vui lòng chọn phương thức thanh toán!", "error");

    const selectedMethod = paymentEl.value;

    setButtonLoading(bookNowBtn, true, "Đang xử lý...");

    try {
        const requestBody = {
            tourId,
            departureDate:  bkDateEl.value,
            numAdults:      adults,
            numChildren:    children,
            pickupLocation: bkPickupEl?.value  || "",
            note:           bkNoteEl?.value    || "",
            paymentMethod:  selectedMethod
        };

        if (activeVoucherCode) {
            requestBody.voucherCode = activeVoucherCode;
        }

        const data = await api("/bookings", {
            method:      "POST",
            showLoader:  true,
            requireAuth: true,
            body:        JSON.stringify(requestBody)
        });

        setButtonLoading(bookNowBtn, false);

        if (!data?.success) {
            // ── Hiện message lỗi rõ ràng từ server ──────────────────────
            const msg = data?.message || "Đặt tour thất bại!";
            toast(msg, "error");

            // Nếu lỗi số dư → gợi ý nạp tiền
            if (msg.includes("Số dư không đủ")) {
                setTimeout(() => toast("💡 Gợi ý: Chọn thanh toán MoMo hoặc VNPay để thanh toán trực tuyến", "info"), 600);
            }
            return;
        }

        const bookingCode = data?.data?.bookingCode || "";
        const paymentUrl  = data?.data?.paymentUrl  || "";
        const totalPrice  = data?.data?.totalPrice  || 0;

        // Reset voucher state
        activeVoucher     = 0;
        activeVoucherCode = null;
        activeVoucherAmt  = 0;

        // Reload điểm
        if (typeof loadCheckinStatus === "function") {
            setTimeout(() => loadCheckinStatus(), 500);
        }

        // Redirect nếu có paymentUrl (MOMO / VNPAY)
        if (paymentUrl) {
            toast(`Đang chuyển đến trang thanh toán ${selectedMethod}... Mã đơn: ${bookingCode}`, "info");
            setTimeout(() => { window.location.href = paymentUrl; }, 900);
            return;
        }

        // Thanh toán thành công qua balance
        toast(`🎉 Đặt tour thành công! Mã đơn: ${bookingCode}`, "success");
        const earned = Math.max(1, Math.round(totalPrice / 100000));
        setTimeout(() => toast(`⭐ Bạn nhận được ${earned} điểm thưởng!`, "info"), 900);
        showPage("mybookings");

    } catch (err) {
        console.error("Booking error:", err);
        setButtonLoading(bookNowBtn, false);
        toast("Đặt tour thất bại, vui lòng thử lại!", "error");
    }
}

// ── Format Itinerary
function formatItinerary(text) {
    if (!text || !text.trim()) return "";

    const isHtml = /<[a-z][\s\S]*>/i.test(text);

    if (isHtml) {
        return `<div class="rich-itinerary">${text}</div>`;
    }

    const esc = (s) => s
        .replace(/&/g,"&amp;").replace(/</g,"&lt;")
        .replace(/>/g,"&gt;").replace(/"/g,"&quot;");

    const highlightTime = (s) => s.replace(
        /\b(\d{1,2}:\d{2})\b/g,
        '<span style="color:#34d399;font-weight:600;background:rgba(52,211,153,0.1);border-radius:4px;padding:0 4px;">$1</span>'
    );

    const lines = text.split(/\r?\n/);
    let html = "";
    let inList = false;
    let dayCount = 0;

    // Detect pattern "Ngày X" hoặc "Day X"
    const dayRe = /^(Ngày|Day)\s*(\d+)/i;

    for (const raw of lines) {
        const line = raw.trim();

        if (!line) {
            if (inList) { html += "</ul>"; inList = false; }
            continue;
        }

        if (dayRe.test(line)) {
            if (inList) { html += "</ul>"; inList = false; }
            dayCount++;
            const dayColors = ["#22d3ee","#34d399","#a78bfa","#fb923c","#f472b6"];
            const color = dayColors[(dayCount - 1) % dayColors.length];
            html += `
            <div style="display:flex;align-items:center;gap:10px;margin:18px 0 10px;padding-bottom:8px;border-bottom:1px solid rgba(255,255,255,0.07);">
              <span style="min-width:28px;height:28px;border-radius:8px;background:${color}22;border:1px solid ${color}55;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:700;color:${color};">
                ${dayCount}
              </span>
              <span style="font-size:14px;font-weight:700;color:${color};">${esc(line)}</span>
            </div>`;
            continue;
        }

        if (/^[A-ZÀÁÂÃÈÉÊÌÍÒÓÔÕÙÚĂĐĨŨƠƯẠ-Ỹ][^•\-\d].*:$/.test(line) ||
            /^(Điểm nhấn|Lưu ý|Landmark|Ghi chú|Bao gồm|Không bao gồm|Dịch vụ|Highlights?)/i.test(line)) {
            if (inList) { html += "</ul>"; inList = false; }
            html += `<p style="margin:14px 0 6px;font-size:13px;font-weight:600;color:#93c5fd;display:flex;align-items:center;gap:6px;">
              <span style="width:4px;height:14px;background:#3b82f6;border-radius:2px;display:inline-block;flex-shrink:0;"></span>
              ${esc(line)}
            </p>`;
            continue;
        }

        if (/^[-•]\s+/.test(line)) {
            if (!inList) {
                html += '<ul style="list-style:none;margin:4px 0 8px;padding:0;display:flex;flex-direction:column;gap:5px;">';
                inList = true;
            }
            const content = highlightTime(esc(line.replace(/^[-•]\s+/, "")));
            html += `<li style="display:flex;gap-8px;align-items:flex-start;font-size:13px;color:#cbd5e1;line-height:1.6;">
              <span style="color:#22d3ee;margin-right:8px;flex-shrink:0;margin-top:2px;">▸</span>
              <span>${content}</span>
            </li>`;
            continue;
        }

        if (/^\d{1,2}:\d{2}/.test(line)) {
            if (!inList) {
                html += '<ul style="list-style:none;margin:4px 0 8px;padding:0;display:flex;flex-direction:column;gap:5px;">';
                inList = true;
            }
            const content = highlightTime(esc(line));
            html += `<li style="display:flex;gap-8px;align-items:flex-start;font-size:13px;color:#cbd5e1;line-height:1.6;">
              <span style="color:#22d3ee;margin-right:8px;flex-shrink:0;margin-top:2px;">▸</span>
              <span>${content}</span>
            </li>`;
            continue;
        }

        if (inList) { html += "</ul>"; inList = false; }
        html += `<p style="font-size:13px;color:#94a3b8;line-height:1.65;margin:4px 0;">${highlightTime(esc(line))}</p>`;
    }

    if (inList) html += "</ul>";
    return html;
}