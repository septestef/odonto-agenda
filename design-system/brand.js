// OdontoAgenda — nombre de marca (definido por el usuario, 2026-11-17).
// Para renombrar: cambiar BRAND_NAME. Todo elemento con [data-brand-name] toma este valor.
(function () {
  "use strict";
  var BRAND_NAME = "OdontoAgenda";
  var BRAND_NAME_PROVISIONAL = false;

  function apply() {
    var nodes = document.querySelectorAll("[data-brand-name]");
    for (var i = 0; i < nodes.length; i++) {
      nodes[i].textContent = BRAND_NAME;
      if (BRAND_NAME_PROVISIONAL) {
        nodes[i].setAttribute("title", "Nombre provisional (TBD)");
        nodes[i].setAttribute("data-provisional", "true");
      }
    }
  }

  window.BRAND = { name: BRAND_NAME, provisional: BRAND_NAME_PROVISIONAL };
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", apply);
  } else {
    apply();
  }
})();
