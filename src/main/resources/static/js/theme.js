window.initTheme = function () {
    const saved = localStorage.getItem("theme") || "dark";
    const isDark = saved === "dark";
    document.documentElement.classList.toggle("dark", isDark);
    document.body.classList.toggle("light", !isDark);
};

window.toggleTheme = function () {
    const isDark = document.documentElement.classList.toggle("dark");
    document.body.classList.toggle("light", !isDark);
    localStorage.setItem("theme", isDark ? "dark" : "light");
};

// Navbar scroll effect
window.initNavbarScroll = function () {
    const navbar = document.getElementById("mainNavbar");
    if (!navbar) return;
    window.addEventListener("scroll", () => {
        navbar.classList.toggle("scrolled", window.scrollY > 20);
    }, { passive: true });
};