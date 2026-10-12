// Antigravity & Gemini settings section.
// Manages Gemini API key and Antigravity / Gemini model selection.

import { Bridge } from "../core/bridge";
import type { Settings } from "../core/state";
import { GEMINI_MODELS } from "../hooks/useChatProvider";
import { h, clear } from "../views/dom";

function statusDot(ok: boolean): HTMLElement {
  const dot = h("i", { class: "dot" });
  dot.classList.add(ok ? "dot-ok" : "dot-warn");
  return dot;
}

export function buildGeminiSection(
  hasKey: boolean,
  settings: Settings,
  save: () => Promise<void>,
): HTMLElement {
  const dot = statusDot(hasKey);
  const state = h("span", {
    class: "hint",
    text: hasKey
      ? "API key saved in the Credential Manager. Local Antigravity CLI (agy) is also supported."
      : "Antigravity CLI (agy) ready. You can also save a Gemini API key below.",
  });

  const field = h("input", {
    type: "password",
    placeholder: hasKey ? "••••••••••••  (stored)" : "AIzaSy...",
    class: "flex-input",
    autocomplete: "off",
    spellcheck: "false",
  }) as HTMLInputElement;

  const saveBtn = h("button", { class: "primary", text: "Save key" });
  const clearBtn = h("button", { class: "danger", text: "Remove" });
  const feedback = h("div", {});

  async function refresh() {
    const present =
      ((await Bridge.secretPresent("gemini-api-key")) ||
        (await Bridge.secretPresent("google-api-key"))) ??
      false;
    dot.classList.toggle("dot-ok", present);
    dot.classList.toggle("dot-warn", !present);
    state.textContent = present
      ? "API key saved in the Credential Manager. Local Antigravity CLI (agy) is also supported."
      : "Antigravity CLI (agy) ready. You can also save a Gemini API key below.";
    field.placeholder = present ? "••••••••••••  (stored)" : "AIzaSy...";
    clearBtn.classList.toggle("hidden", !present);
  }

  saveBtn.addEventListener("click", async () => {
    const value = field.value.trim();
    if (!value) return;
    clear(feedback);
    try {
      await Bridge.secretSet("gemini-api-key", value);
      field.value = "";
      feedback.append(h("div", { class: "notice ok", text: "Saved. Stored securely." }));
      await refresh();
    } catch (err) {
      feedback.append(h("div", { class: "notice err", text: `Could not save: ${String(err)}` }));
    }
  });

  clearBtn.addEventListener("click", async () => {
    clear(feedback);
    try {
      await Bridge.secretClear("gemini-api-key");
      await Bridge.secretClear("google-api-key");
      feedback.append(h("div", { class: "notice ok", text: "Key removed." }));
      await refresh();
    } catch (err) {
      feedback.append(h("div", { class: "notice err", text: `Could not remove: ${String(err)}` }));
    }
  });

  const currentModel = settings.chatModels?.["google"] ?? "gemini-2.0-flash";
  const model = h("select", {}) as HTMLSelectElement;
  for (const [id, label] of GEMINI_MODELS) model.append(h("option", { value: id, text: label }));
  if (!GEMINI_MODELS.some(([id]) => id === currentModel)) {
    model.append(h("option", { value: currentModel, text: currentModel }));
  }
  model.value = currentModel;
  model.addEventListener("change", () => {
    if (!settings.chatModels) settings.chatModels = {};
    settings.chatModels["google"] = model.value;
    void save();
  });

  clearBtn.classList.toggle("hidden", !hasKey);

  return h(
    "section",
    {},
    h("h2", {}, dot, h("span", { text: "Antigravity / Gemini" })),
    state,
    h("div", { class: "row" }, h("label", { text: "API key" }), field, saveBtn, clearBtn),
    h("div", { class: "row" }, h("label", { text: "Model" }), model, h("span", { class: "hint", text: "1M-2M token context" })),
    feedback,
  );
}
