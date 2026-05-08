let loginTab, registerTab, loginForm, registerForm;
let guestActions, userMenu, myBookingsLink, userDisplayName, userDropdown;

document.addEventListener("DOMContentLoaded", () => {
    loginTab        = document.getElementById("loginTab");
    registerTab     = document.getElementById("registerTab");
    loginForm       = document.getElementById("loginForm");
    registerForm    = document.getElementById("registerForm");
    guestActions    = document.getElementById("guestActions");
    userMenu        = document.getElementById("userMenu");
    userDropdown    = document.getElementById("userDropdown");
    myBookingsLink  = document.getElementById("myBookingsLink");
    userDisplayName = document.getElementById("userDisplayName");

    updateNavAuth();
    switchAuthTab("login");
});

//Navbar auth state
function updateNavAuth() {
    const _guestActions   = guestActions   || document.getElementById("guestActions");
    const _userMenu       = userMenu       || document.getElementById("userMenu");
    const _myBookings     = myBookingsLink || document.getElementById("myBookingsLink");
    const _userDispName   = userDisplayName || document.getElementById("userDisplayName");

    const user = JSON.parse(localStorage.getItem("st_user") || "null");
    window.currentUser = user;

    if (user) {
        if (_guestActions) _guestActions.classList.add("hidden");
        if (_userMenu)     _userMenu.classList.remove("hidden");
        if (_myBookings)   _myBookings.classList.remove("hidden");
        if (_userDispName) {
            const parts = (user.fullName || "").trim().split(" ");
            _userDispName.textContent = parts.filter(Boolean).pop() || user.email || "User";
        }
    } else {
        if (_guestActions) _guestActions.classList.remove("hidden");
        if (_userMenu)     _userMenu.classList.add("hidden");
        if (_myBookings)   _myBookings.classList.add("hidden");
    }
}

// Dropdown
function toggleDropdown() {
    const el = userDropdown || document.getElementById("userDropdown");
    el?.classList.toggle("hidden");
}
function closeDropdown() {
    const el = userDropdown || document.getElementById("userDropdown");
    el?.classList.add("hidden");
}
document.addEventListener("click", (e) => {
    const _userMenu    = document.getElementById("userMenu");
    const _userDropdown = document.getElementById("userDropdown");
    if (_userMenu && _userDropdown && !_userMenu.contains(e.target)) {
        closeDropdown();
    }
});

// Auth tab switching
function switchAuthTab(tab) {
    const _loginTab     = loginTab     || document.getElementById("loginTab");
    const _registerTab  = registerTab  || document.getElementById("registerTab");
    const _loginForm    = loginForm    || document.getElementById("loginForm");
    const _registerForm = registerForm || document.getElementById("registerForm");
    if (!_loginTab || !_registerTab || !_loginForm || !_registerForm) return;

    if (tab === "login") {
        _loginTab.classList.add("active");
        _registerTab.classList.remove("active");
        _loginForm.classList.remove("hidden");
        _registerForm.classList.add("hidden");
    } else {
        _registerTab.classList.add("active");
        _loginTab.classList.remove("active");
        _registerForm.classList.remove("hidden");
        _loginForm.classList.add("hidden");
    }
}

//requireAuth
function requireAuth(action) {
    if (!getToken()) {
        toast(t("toast.loginFirst") || "Vui lòng đăng nhập!", "error");
        showPage("auth");
        return;
    }
    action?.();
}

// Logout
function logout() {
    localStorage.removeItem("st_token");
    localStorage.removeItem("st_user");
    window.currentUser = null;
    updateNavAuth();
    showPage("home");
    toast(t("toast.loggedOut") || "Đã đăng xuất!", "success");
}

//LOGIN
async function doLogin() {
    const emailEl    = document.getElementById("loginEmail");
    const passwordEl = document.getElementById("loginPassword");
    const btn        = document.getElementById("loginBtn");

    const email    = emailEl?.value?.trim();
    const password = passwordEl?.value;

    if (!email || !password) {
        return toast(t("toast.fillRequired") || "Vui lòng điền đầy đủ thông tin!", "error");
    }

    setButtonLoading(btn, true, t("common.loading") || "Đang xử lý...");

    const data = await api("/auth/login", {
        method: "POST",
        showLoader: true,
        body: JSON.stringify({ email, password })
    });

    setButtonLoading(btn, false);

    if (!data.success) {
        return toast(
            data.status === 400
                ? (t("toast.invalidCredentials") || "Email hoặc mật khẩu không đúng!")
                : (data.message || t("toast.loginFail") || "Đăng nhập thất bại!"),
            "error"
        );
    }

    const token = data.data?.token;
    if (!token) {
        return toast(t("toast.loginFail") || "Không nhận được token, vui lòng thử lại!", "error");
    }

    localStorage.setItem("st_token", token);
    localStorage.setItem("st_user", JSON.stringify({
        id:       data.data.id,
        fullName: data.data.fullName,
        email:    data.data.email,
        role:     data.data.role
    }));

    updateNavAuth();
    showPage("home");
    toast(data.message || "Đăng nhập thành công!", "success");
}

// REGISTER
async function doRegister() {
    const nameEl     = document.getElementById("regName");
    const emailEl    = document.getElementById("regEmail");
    const passwordEl = document.getElementById("regPassword");
    const phoneVal   = document.getElementById("regPhone")?.value || "";

    const btn = document.getElementById("registerBtn");

    const fullName = nameEl?.value?.trim();
    const email    = emailEl?.value?.trim();
    const password = passwordEl?.value;

    if (!fullName || !email || !password) {
        return toast(t("toast.fillRequired") || "Vui lòng điền đầy đủ thông tin!", "error");
    }
    if (password.length < 6) {
        return toast("Mật khẩu phải có ít nhất 6 ký tự!", "error");
    }

    setButtonLoading(btn, true, t("common.loading") || "Đang xử lý...");

    const data = await api("/auth/register", {
        method: "POST",
        showLoader: true,
        body: JSON.stringify({
            fullName,
            email,
            password,
            phone: phoneVal
        })
    });

    setButtonLoading(btn, false);

    if (!data.success) {
        return toast(data.message || t("toast.registerFail") || "Đăng ký thất bại!", "error");
    }

    toast(t("toast.registerSuccess") || "Đăng ký thành công! Vui lòng đăng nhập.", "success");
    switchAuthTab("login");

    const loginEmailEl = document.getElementById("loginEmail");
    if (loginEmailEl) loginEmailEl.value = email;
}
