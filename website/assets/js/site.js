/* Weight Tracker site. No framework, no tracking, nothing loaded from anywhere
   else. Everything here degrades to plain HTML when JavaScript is off. */
(function () {
  "use strict";

  var root = document.documentElement;
  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---- year ------------------------------------------------------------ */
  document.querySelectorAll("[data-year]").forEach(function (el) {
    el.textContent = String(new Date().getFullYear());
  });

  /* ---- theme: system by default, one tap to pin light or dark ---------- */
  var toggle = document.querySelector("[data-theme-toggle]");
  function currentTheme() {
    var pinned = root.getAttribute("data-theme");
    if (pinned) return pinned;
    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }
  if (toggle) {
    toggle.addEventListener("click", function () {
      var next = currentTheme() === "dark" ? "light" : "dark";
      root.setAttribute("data-theme", next);
      try { localStorage.setItem("wt-theme", next); } catch (e) {}
    });
  }

  /* ---- reveal on scroll ------------------------------------------------ */
  var revealed = document.querySelectorAll(".reveal");
  if (!reduceMotion && "IntersectionObserver" in window && revealed.length) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-in");
          io.unobserve(entry.target);
        }
      });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.08 });
    revealed.forEach(function (el) { io.observe(el); });
  } else {
    revealed.forEach(function (el) { el.classList.add("is-in"); });
  }

  /* ---- hero phrases ---------------------------------------------------- */
  var rotator = document.querySelector("[data-rotate]");
  if (rotator && !reduceMotion) {
    var phrases = [];
    try { phrases = JSON.parse(rotator.getAttribute("data-rotate")); } catch (e) {}
    if (phrases.length > 1) {
      var i = 0;
      setInterval(function () {
        rotator.classList.add("is-out");
        setTimeout(function () {
          i = (i + 1) % phrases.length;
          rotator.textContent = phrases[i];
          rotator.classList.remove("is-out");
        }, 350);
      }, 4200);
    }
  }

  /* ---- the status demo: one switch, everything turns amber ------------- */
  document.querySelectorAll("[data-status-demo]").forEach(function (demo) {
    var button = demo.querySelector("[data-status-toggle]");
    var label = demo.querySelector("[data-status-label]");
    if (!button) return;
    button.addEventListener("click", function () {
      var behind = demo.classList.toggle("is-behind");
      button.setAttribute("aria-pressed", behind ? "true" : "false");
      button.textContent = behind ? "Back on the line" : "Skip the scale for a week";
      if (label) label.textContent = behind ? "0.7 kg behind" : "0.4 kg ahead";
      demo.querySelectorAll("[data-ahead]").forEach(function (el) {
        el.textContent = el.getAttribute(behind ? "data-behind" : "data-ahead");
      });
    });
  });

  /* ---- replay the hero chart on tap ------------------------------------ */
  document.querySelectorAll("[data-chart]").forEach(function (chart) {
    chart.addEventListener("click", function () {
      if (reduceMotion) return;
      chart.classList.remove("is-drawn");
      void chart.offsetWidth; /* restart the CSS animation */
      chart.classList.add("is-drawn");
    });
    chart.classList.add("is-drawn");
  });

  /* ---- contact form: report a rejected submit here, not on a JSON page - */
  var form = document.querySelector("form[data-contact]");
  if (form && window.fetch) {
    var errorBox = form.querySelector("[data-form-error]");
    var submit = form.querySelector("button[type=submit]");
    form.addEventListener("submit", function (event) {
      event.preventDefault();
      if (errorBox) { errorBox.hidden = true; errorBox.textContent = ""; }
      if (submit) { submit.disabled = true; submit.textContent = "Sending…"; }
      var data = new FormData(form);
      fetch(form.action, {
        method: "POST",
        body: data,
        headers: { "Accept": "application/json" }
      }).then(function (r) {
        return r.json().then(function (json) { return { ok: r.ok, json: json }; });
      }).then(function (res) {
        if (res.ok && res.json && res.json.success) {
          window.location.href = form.querySelector("input[name=redirect]").value;
          return;
        }
        throw new Error((res.json && res.json.message) || "The form service rejected the message.");
      }).catch(function (err) {
        if (errorBox) {
          errorBox.textContent = "Not sent: " + err.message + " You can also write to the address in the privacy policy.";
          errorBox.hidden = false;
        }
        if (submit) { submit.disabled = false; submit.textContent = "Send message"; }
      });
    });
  }
})();
