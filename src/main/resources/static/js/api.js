const API = "/api";

// Helpers
const getToken   = () => localStorage.getItem("st_token");
const fmtPrice   = (v) => Number(v || 0).toLocaleString("vi-VN") + "đ";
const starHtml   = (r) => { const x = Math.round(parseFloat(r) || 0); return "★".repeat(x) + "☆".repeat(5 - x); };
const escapeHtml = (value) => String(value || "")
    .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");

function getTourImages(tour) {
    if (!tour || !Array.isArray(tour.images)) return [];
    return tour.images.map((img) => (img && img.imageUrl ? img.imageUrl : null))
        .filter((url) => typeof url === "string" && url.trim().length > 0);
}

function formatItinerary(rawText) {
    if (!rawText || !rawText.trim()) return `<p class="text-slate-300">Chưa có lịch trình</p>`;
    const sections = rawText.split("\n\n").map((s) => s.trim()).filter(Boolean);
    return sections.map((section) => {
        const lines = section.split("\n").map((line) => line.trim()).filter(Boolean);
        if (!lines.length) return "";
        const title = lines[0].endsWith(":") ? lines[0] : null;
        const items = title ? lines.slice(1) : lines;
        const bulletItems = items.filter((line) => line.startsWith("- ") || line.startsWith("• "));
        const textItems   = items.filter((line) => !line.startsWith("- ") && !line.startsWith("• "));
        const titleHtml  = title ? `<h4 class="mb-2 mt-4 text-base font-bold text-cyan-300">${escapeHtml(title.slice(0, -1))}</h4>` : "";
        const textHtml   = textItems.map((line) => `<p>${escapeHtml(line)}</p>`).join("");
        const bulletHtml = bulletItems.length
            ? `<ul>${bulletItems.map((line) => `<li>${escapeHtml(line.substring(2))}</li>`).join("")}</ul>`
            : "";
        return `${titleHtml}${textHtml}${bulletHtml}`;
    }).join("");
}

// Toast
function toast(message, type = "success") {
    const palette = { success: "from-emerald-500 to-teal-500", error: "from-rose-500 to-red-500", info: "from-cyan-500 to-indigo-500" };
    const node = document.createElement("div");
    node.className = `pointer-events-auto rounded-xl bg-gradient-to-r ${palette[type] || palette.info} px-4 py-3 text-sm font-semibold text-white shadow-xl`;
    node.textContent = message;
    const stack = document.getElementById("toastStack") || (() => {
        const el = document.createElement("div");
        el.id = "toastStack";
        el.className = "fixed right-4 top-20 z-[120] space-y-2";
        document.body.appendChild(el);
        return el;
    })();
    stack.appendChild(node);
    setTimeout(() => node.remove(), 2600);
}

// Loading
function setGlobalLoading(isLoading) {
    const loader = document.getElementById("globalLoader");
    if (!loader) return;
    loader.classList.toggle("hidden", !isLoading);
    loader.classList.toggle("flex", isLoading);
}

function setButtonLoading(buttonEl, isLoading, text) {
    if (!buttonEl) return;
    if (isLoading) {
        buttonEl.dataset.originalText = buttonEl.innerHTML;
        buttonEl.disabled = true;
        buttonEl.innerHTML = `<i class="fa-solid fa-spinner fa-spin mr-2"></i>${text || "Đang xử lý..."}`;
        return;
    }
    buttonEl.disabled = false;
    if (buttonEl.dataset.originalText) buttonEl.innerHTML = buttonEl.dataset.originalText;
}

// Auth session
function clearAuthSession(showToastMsg = true) {
    localStorage.removeItem("st_token");
    localStorage.removeItem("st_user");
    if (typeof updateNavAuth === "function") updateNavAuth();
    if (showToastMsg) toast("Phiên đăng nhập hết hạn, vui lòng đăng nhập lại!", "error");
    if (typeof showPage === "function") showPage("auth");
}

// API caller
async function api(endpoint, options = {}) {
    const {
        method       = "GET",
        body         = null,
        showLoader   = false,
        requireAuth  = false,
        headers: extraHeaders = {}
    } = options;

    if (showLoader) setGlobalLoading(true);

    try {
        const token = getToken();

        // Nếu requireAuth mà không có token → redirect login ngay
        if (requireAuth && !token) {
            clearAuthSession(true);
            return { success: false, message: "Chưa đăng nhập" };
        }

        const headers = {
            "Content-Type": "application/json",
            ...extraHeaders
        };

        if (token) {
            headers["Authorization"] = "Bearer " + token;
        }

        const res = await fetch(API + endpoint, {
            method,
            headers,
            body: body || undefined
        });

        // Token hết hạn hoặc invalid
        if (res.status === 401) {
            clearAuthSession(true);
            return { success: false, status: 401, message: "Phiên đăng nhập hết hạn" };
        }

        // Thử parse JSON
        let json;
        const contentType = res.headers.get("content-type") || "";
        if (contentType.includes("application/json")) {
            json = await res.json();
        } else {
            const text = await res.text();
            json = { success: res.ok, message: text };
        }

        // Nếu backend trả success=false
        if (!res.ok && json.success === undefined) {
            json.success = false;
        }

        json.status = res.status;
        return json;

    } catch (err) {
        console.error("API error:", endpoint, err);
        return { success: false, message: "Lỗi kết nối, vui lòng thử lại!" };
    } finally {
        if (showLoader) setGlobalLoading(false);
    }
}

// Skeleton cards helper
function skeletonCards(count = 3) {
    return Array(count).fill(0).map(() => `
    <div class="glass rounded-2xl p-3 animate-pulse">
      <div class="skeleton h-44 rounded-xl"></div>
      <div class="skeleton mt-3 h-5 w-3/4 rounded-lg"></div>
      <div class="skeleton mt-2 h-4 w-1/2 rounded-lg"></div>
      <div class="skeleton mt-3 h-8 rounded-xl"></div>
    </div>`).join("");
}