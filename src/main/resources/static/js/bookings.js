function timeline(status) {
    const activeConfirmed = ["CONFIRMED", "PAID", "COMPLETED"].includes(status);
    const activeCompleted = status === "COMPLETED";
    return `
    <div class="mt-3 flex items-center gap-1.5 text-xs flex-wrap">
      <div class="timeline-step active rounded-full border px-3 py-1">Đã đặt</div>
      <i class="fa-solid fa-chevron-right text-slate-500 text-[10px]"></i>
      <div class="timeline-step ${activeConfirmed ? "active" : "pending"} rounded-full border px-3 py-1">Đã xác nhận</div>
      <i class="fa-solid fa-chevron-right text-slate-500 text-[10px]"></i>
      <div class="timeline-step ${activeCompleted ? "active" : "pending"} rounded-full border px-3 py-1">Hoàn thành</div>
    </div>`;
}

function statusBadge(status) {
    const map = {
        PENDING:   { label: "Chờ xử lý",    cls: "bg-yellow-500/20 text-yellow-300 border-yellow-500/40" },
        PAID:      { label: "Đã thanh toán", cls: "bg-emerald-500/20 text-emerald-300 border-emerald-500/40" },
        CONFIRMED: { label: "Đã xác nhận",  cls: "bg-cyan-500/20 text-cyan-300 border-cyan-500/40" },
        COMPLETED: { label: "Hoàn thành",   cls: "bg-green-500/20 text-green-300 border-green-500/40" },
        CANCELLED: { label: "Đã hủy",       cls: "bg-rose-500/20 text-rose-300 border-rose-500/40" },
        FAILED:    { label: "Thất bại",      cls: "bg-red-500/20 text-red-300 border-red-500/40" }
    };
    const s = map[status] || { label: status, cls: "bg-white/10 text-slate-300 border-white/20" };
    return `<span class="rounded-full border px-2.5 py-0.5 text-xs font-semibold ${s.cls}">${s.label}</span>`;
}

async function loadMyBookings() {
    const listEl = document.getElementById("bookingsList");
    if (!listEl) return;

    listEl.innerHTML = skeletonCards(3);

    const data = await api("/bookings/my", { showLoader: true, requireAuth: true });
    if (!data.success) {
        listEl.innerHTML = `<div class="glass rounded-2xl p-12 text-center text-slate-400">
            <i class="fa-solid fa-triangle-exclamation text-3xl mb-3 block text-rose-400"></i>
            Không thể tải lịch sử đặt tour.</div>`;
        return;
    }

    const list = data.data || [];
    if (!list.length) {
        listEl.innerHTML = `<div class="glass rounded-2xl p-12 text-center text-slate-400">
            <i class="fa-solid fa-suitcase-rolling text-4xl mb-4 block text-slate-600"></i>
            <p class="text-lg font-semibold">Bạn chưa có đơn đặt tour nào</p>
            <p class="text-sm mt-1 mb-5">Hãy khám phá và đặt tour ngay hôm nay!</p>
            <button onclick="showPage('tours')" class="rounded-xl bg-gradient-to-r from-emerald-400 to-cyan-400 px-6 py-2.5 font-semibold text-slate-900">
                Khám phá Tours
            </button>
        </div>`;
        return;
    }

    listEl.innerHTML = list.map((b) => `
    <article class="glass rounded-2xl p-5 transition hover:border-white/20">
      <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div class="flex-1 min-w-0">
          <div class="flex items-start gap-3">
            <div class="flex-shrink-0 mt-0.5">
              <div class="h-10 w-10 rounded-xl bg-gradient-to-br from-cyan-500/20 to-emerald-500/20 border border-white/10 flex items-center justify-center">
                <i class="fa-solid fa-plane text-cyan-400 text-sm"></i>
              </div>
            </div>
            <div class="flex-1 min-w-0">
              <h4 class="text-base font-bold truncate">${escapeHtml(b.tourName || "")}</h4>
              <p class="text-xs text-slate-400 mt-0.5">
                <span class="font-mono">#${b.bookingCode}</span>
                <span class="mx-1.5">·</span>
                <i class="fa-solid fa-calendar-day mr-1"></i>${b.departureDate}
              </p>
              <div class="mt-2 flex flex-wrap items-center gap-2">
                ${statusBadge(b.status)}
                <span class="rounded-full bg-white/10 px-2.5 py-0.5 text-xs">
                  <i class="fa-solid fa-users mr-1"></i>${b.numAdults} NL${b.numChildren > 0 ? ` · ${b.numChildren} TE` : ""}
                </span>
                <span class="text-base font-black text-cyan-300">${fmtPrice(b.totalPrice)}</span>
                ${b.paymentMethod ? `<span class="rounded-full bg-white/10 px-2.5 py-0.5 text-xs text-slate-400">${b.paymentMethod}</span>` : ""}
              </div>
              ${timeline(b.status)}
            </div>
          </div>
        </div>
        <div class="flex gap-2 flex-shrink-0 md:mt-0">
          <button class="rounded-xl bg-white/10 px-3 py-2 text-sm hover:bg-white/20 transition"
            onclick='openBookingModal(${JSON.stringify(b).replace(/'/g,"&#39;")})'>
            <i class="fa-solid fa-eye mr-1"></i>Chi tiết
          </button>
          ${b.status === "PENDING"
        ? `<button class="rounded-xl bg-rose-500/20 border border-rose-500/30 px-3 py-2 text-sm text-rose-300 hover:bg-rose-500/30 transition"
                 onclick="cancelBooking(${b.id})">
                 <i class="fa-solid fa-xmark mr-1"></i>Hủy đơn
               </button>` : ""}
        </div>
      </div>
    </article>`
    ).join("");
}

function openBookingModal(booking) {
    const modal     = document.getElementById("bookingModal");
    const modalBody = document.getElementById("bookingModalBody");
    if (!modal || !modalBody) return;

    modal.classList.remove("hidden");
    modal.classList.add("flex");

    modalBody.innerHTML = `
    <div class="space-y-3 pt-2">
      <div class="grid grid-cols-2 gap-3">
        <div class="rounded-xl bg-white/5 p-3">
          <p class="text-xs text-slate-500 mb-1">Mã đơn</p>
          <p class="font-mono font-bold text-cyan-300">#${booking.bookingCode}</p>
        </div>
        <div class="rounded-xl bg-white/5 p-3">
          <p class="text-xs text-slate-500 mb-1">Trạng thái</p>
          <div>${statusBadge(booking.status)}</div>
        </div>
      </div>
      <div class="rounded-xl bg-white/5 p-3">
        <p class="text-xs text-slate-500 mb-1">Tour</p>
        <p class="font-semibold">${escapeHtml(booking.tourName || "")}</p>
      </div>
      <div class="grid grid-cols-2 gap-3">
        <div class="rounded-xl bg-white/5 p-3">
          <p class="text-xs text-slate-500 mb-1">Ngày khởi hành</p>
          <p class="font-semibold"><i class="fa-solid fa-calendar mr-1 text-cyan-400"></i>${booking.departureDate}</p>
        </div>
        <div class="rounded-xl bg-white/5 p-3">
          <p class="text-xs text-slate-500 mb-1">Số khách</p>
          <p class="font-semibold">${booking.numAdults} người lớn${booking.numChildren > 0 ? ` · ${booking.numChildren} trẻ em` : ""}</p>
        </div>
      </div>
      <div class="grid grid-cols-2 gap-3">
        <div class="rounded-xl bg-white/5 p-3">
          <p class="text-xs text-slate-500 mb-1">Thanh toán</p>
          <p class="font-semibold">${booking.paymentMethod || "N/A"}</p>
        </div>
        <div class="rounded-xl bg-gradient-to-br from-cyan-500/10 to-emerald-500/10 border border-cyan-500/20 p-3">
          <p class="text-xs text-slate-500 mb-1">Tổng tiền</p>
          <p class="text-lg font-black text-cyan-300">${fmtPrice(booking.totalPrice)}</p>
        </div>
      </div>
      <p class="text-xs text-slate-600 text-right">Đặt lúc: ${booking.createdAt ? new Date(booking.createdAt).toLocaleString("vi-VN") : ""}</p>
    </div>`;
}

function closeBookingModal() {
    const modal = document.getElementById("bookingModal");
    modal?.classList.add("hidden");
    modal?.classList.remove("flex");
}

async function cancelBooking(id) {
    if (!confirm("Bạn có chắc muốn hủy đơn này không?\nĐơn đã hủy không thể khôi phục.")) return;
    const data = await api(`/bookings/${id}/cancel`, { method: "PUT", showLoader: true, requireAuth: true });
    if (!data.success) return toast(data.message || "Hủy đơn thất bại!", "error");
    toast("Đã hủy đơn thành công!", "success");
    loadMyBookings();
}