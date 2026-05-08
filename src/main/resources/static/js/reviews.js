let allReviews = [];

// Generate avatar URL from user name or email
function getAvatarUrl(name, email) {
    const seed = encodeURIComponent(email || name || "user");
    return `https://api.dicebear.com/7.x/initials/svg?seed=${seed}&backgroundColor=22d3ee,10b981,a855f7,ec4899,6366f1&backgroundType=gradientLinear`;
}

// Format date
function formatDate(dateStr) {
    if (!dateStr) return "";
    const date = new Date(dateStr);
    return date.toLocaleDateString("en-US", { day: "numeric", month: "short", year: "numeric" });
}

// Generate star rating HTML
function getStarRatingHtml(rating) {
    const stars = Math.round(parseFloat(rating) || 0);
    let html = "";
    for (let i = 1; i <= 5; i++) {
        html += i <= stars ? '<span class="text-amber-400">★</span>' : '<span class="text-slate-600">★</span>';
    }
    return html;
}

// Render single review card
function reviewCardHtml(review) {
    const avatarUrl = getAvatarUrl(review.userName, review.userEmail);
    const tourName = review.tourName || "Tour";
    return `
    <article class="review-card glass rounded-2xl p-5 transition-all duration-300 hover:scale-[1.02] hover:shadow-xl hover:shadow-cyan-500/10 cursor-pointer">
        <div class="flex items-start gap-4">
            <img src="${avatarUrl}" alt="${review.userName}" class="h-12 w-12 rounded-full object-cover ring-2 ring-cyan-400/30" />
            <div class="flex-1 min-w-0">
                <h4 class="font-bold truncate">${review.userName}</h4>
                <div class="mt-1 flex items-center gap-2 text-sm">
                    ${getStarRatingHtml(review.rating)}
                    <span class="text-slate-400 text-xs">${review.rating}/5</span>
                </div>
            </div>
            <span class="rounded-full bg-white/10 px-2 py-1 text-xs">${tourName}</span>
        </div>
        <div class="mt-4">
            <h5 class="font-semibold text-cyan-200 line-clamp-1">${review.title}</h5>
            <p class="mt-2 text-sm text-slate-300 line-clamp-3">${review.body}</p>
        </div>
        <div class="mt-4 flex items-center justify-between text-xs text-slate-500">
            <span>${formatDate(review.createdAt)}</span>
            <span>${review.helpfulCount || 0} found helpful</span>
        </div>
    </article>`;
}

// Render reviews grid
function renderReviewsGrid(reviews, containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;
    if (!reviews || reviews.length === 0) {
        container.innerHTML = `
        <div class="glass col-span-full rounded-2xl p-12 text-center">
            <i class="fa-solid fa-star text-4xl text-slate-600 mb-3"></i>
            <p class="text-slate-400">No reviews yet. Be the first to share your experience!</p>
        </div>`;
        return;
    }
    container.innerHTML = reviews.map(reviewCardHtml).join("");
}

// Render featured reviews on home page
function renderFeaturedReviews() {
    const container = document.getElementById("featuredReviews");
    if (!container || !allReviews.length) return;
    container.innerHTML = allReviews.slice(0, 6).map(reviewCardHtml).join("");
}

// Load all reviews from API
async function loadAllReviews(showLoader = false) {
    const data = await api("/reviews", { showLoader });
    if (data.success && data.data) {
        allReviews = data.data || [];
        return allReviews;
    }
    return [];
}

// Load featured reviews for home page
async function loadFeaturedReviews() {
    const data = await api("/reviews?limit=6", { showLoader: false });
    if (data.success && data.data) {
        allReviews = data.data || [];
    }
    renderFeaturedReviews();
}

// Load reviews page
async function loadReviewsPage() {
    const container = document.getElementById("reviewsGrid");
    if (!container) return;
    if (!allReviews.length) {
        container.innerHTML = `
        <div class="glass col-span-full rounded-2xl p-12 text-center">
            <i class="fa-solid fa-spinner fa-spin text-3xl text-cyan-400 mb-3"></i>
            <p class="text-slate-400">Loading reviews...</p>
        </div>`;
        await loadAllReviews(true);
    }
    renderReviewsGrid(allReviews, "reviewsGrid");
}

// Initialize reviews on home page if element exists
document.addEventListener("DOMContentLoaded", function() {
    const featuredContainer = document.getElementById("featuredReviews");
    if (featuredContainer) {
        loadFeaturedReviews();
    }
});