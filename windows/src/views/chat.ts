// Chat view — DOM port of PromptView / ChatBubble / TypingDotsView /
// ModelPickerView from IslandViewContent.swift.
// Supports Claude and Antigravity / Gemini with model usage meter.

import { h, svg, clear } from "./dom";
import { ICONS } from "./icons";
import { renderMarkdown } from "./markdown";
import { Bridge, onEvent, type ChatContext, type ModelInfo } from "../core/bridge";
import {
  activeModel, pickModel, providerDef, visibleProviders, withModel, type ProviderDef,
} from "../core/providers";
import { Sound } from "../core/sound";
import { chipLabel, loadSubscriptions, subscribedProviders, subscription } from "../core/subscriptions";
import { State, type ChatMessage } from "../core/state";
import {
  formatTokens,
  getCurrentModelUsage,
  sendChatMessage,
} from "../hooks/useChatProvider";
import type { ViewHost } from "./views";
import { N_, t, tl } from "../i18n/i18n";

const STRINGS = {
  placeholderFirst: N_("Ask me anything…"),
  placeholderNext: N_("Continue…"),
  send: N_("Send"),
  switchModel: N_("Switch provider or model"),
  noModel: N_("Choose a model"),
  loading: N_("Loading models…"),
  noKey: N_("No API key — add it in Settings."),
  openSettings: N_("Open Settings"),
};

let nextId = 1;

function bubble(message: ChatMessage): HTMLElement {
  if (message.role === "user") {
    return h(
      "div",
      { class: "chat-row user" },
      h("div", { class: "bubble", text: message.content }),
    );
  }
  const reply = h("div", { class: "reply" });
  renderMarkdown(reply, message.content);
  return h("div", { class: "chat-row" }, reply);
}

function typingDots(): HTMLElement {
  return h(
    "div",
    { class: "chat-row" },
    h("div", { class: "typing" }, h("i"), h("i"), h("i")),
  );
}

function contextChip(label: string): HTMLElement {
  const chip = h("div", { class: "chip" }, h("i", { class: "chip-dot" }), h("span", { text: label }));
  requestAnimationFrame(() => chip.classList.add("settled"));
  return chip;
}

function saveSettings() {
  void Bridge.saveSettings(State.settings);
}

// ── Model picker ──────────────────────────────────────────────────────────────

interface Picker {
  el: HTMLElement;
  open(): void;
  close(): void;
  readonly isOpen: boolean;
}

function buildPicker(onChange: () => void): Picker {
  const chips = h("div", { class: "picker-chips" });
  const list = h("div", { class: "picker-list" });
  const el = h("div", { class: "picker" }, chips, h("div", { class: "picker-rule" }), list);

  const cache = new Map<string, ModelInfo[]>();
  let isOpen = false;
  let request = 0;

  function drawChips() {
    clear(chips);
    for (const p of visibleProviders(State.settings, subscribedProviders())) {
      const on = p.id === State.settings.chatProvider;
      const chip = h(
        "button",
        { class: on ? `picker-chip on provider-${p.id}` : `picker-chip provider-${p.id}` },
        h("i", { class: "picker-dot" }),
        h("span", { text: t(p.name) }),
      );
      chip.addEventListener("click", () => {
        if (p.id === State.settings.chatProvider) return;
        State.settings = { ...State.settings, chatProvider: p.id };
        saveSettings();
        Sound.play("pop");
        drawChips();
        void loadModels();
        onChange();
      });
      chips.append(chip);
    }
  }

  function status(text: string, withSettings = false) {
    clear(list);
    const line = h("div", { class: "picker-status", text });
    if (withSettings) {
      line.append(
        h("button", {
          class: "picker-link",
          text: tl(STRINGS.openSettings),
          onclick: () => void Bridge.openSettingsWindow(),
        }),
      );
    }
    list.append(line);
  }

  function drawModels(p: ProviderDef, models: ModelInfo[]) {
    clear(list);
    // Through a subscription, a tool other than Claude Code uses its own model ("").
    const ownModel = subscription(p.id)?.on && p.id !== "anthropic";
    const current = ownModel ? "" : activeModel(State.settings);
    for (const m of models) {
      const on = m.id === current;
      const row = h(
        "button",
        { class: on ? "picker-model picker-row on" : "picker-model picker-row", title: m.id },
        h("span", { class: "picker-model-name picker-name", text: m.label || m.id }),
        on ? svg(ICONS.check, 10, { stroke: 2.2 }) : h("span"),
      );
      row.addEventListener("click", () => {
        if (!m.id) return close();
        State.settings = withModel(State.settings, p.id, m.id);
        saveSettings();
        Sound.play("pop");
        close();
        onChange();
      });
      list.append(row);
    }
  }

  async function loadModels() {
    const p = providerDef(State.settings.chatProvider);
    const ticket = ++request;
    const current = activeModel(State.settings);
    const subscribed = subscription(p.id)?.on ?? false;
    const cacheKey = `${p.id}:${subscribed}`;
    const cached = p.urlField ? undefined : cache.get(cacheKey);
    if (cached) {
      drawModels(p, cached);
      return;
    }
    // Nothing is asked of a provider that has no key yet (its subscription needs none).
    if (!subscribed && p.key && !(await Bridge.keyReady(p.key))) {
      if (ticket === request) status(t(STRINGS.noKey), true);
      return;
    }
    if (ticket !== request) return;
    status(t(STRINGS.loading));
    try {
      const models = await Bridge.chatModels(p.id);
      if (ticket !== request) return;
      if (!p.urlField) cache.set(cacheKey, models);
      const keep = subscribed && p.id !== "anthropic" ? null : pickModel(p, models.map((m) => m.id), current);
      if (keep && keep !== current) {
        State.settings = withModel(State.settings, p.id, keep);
        saveSettings();
        onChange();
      }
      drawModels(p, models);
    } catch (err) {
      if (ticket !== request) return;
      const text = String(err).replace(/^Error:\s*/, "");
      const withSettings = text === t(STRINGS.noKey) || p.key != null;
      status(text, withSettings);
    }
  }

  function open() {
    if (isOpen) return;
    isOpen = true;
    el.classList.add("open");
    drawChips();
    onChange();
    // Which subscriptions are on may have changed in Settings: ask, then list.
    void loadSubscriptions().then(() => {
      if (!isOpen) return;
      drawChips();
      onChange();
      void loadModels();
    });
  }

  function close() {
    if (!isOpen) return;
    isOpen = false;
    el.classList.remove("open");
    onChange();
  }

  return {
    el,
    open,
    close,
    get isOpen() {
      return isOpen;
    },
  };
}

// ── View ──────────────────────────────────────────────────────────────────────

export function buildPrompt(onHeightChange: () => void): ViewHost {
  const chipRow = h("div", { class: "chip-row" });
  const log = h("div", { class: "chat-log" });
  const input = h("input", {
    type: "text",
    class: "chat-input",
    placeholder: t(STRINGS.placeholderFirst),
    spellcheck: "false",
  }) as HTMLInputElement;
  const send = h("button", { class: "send-btn", title: tl(STRINGS.send) }, svg(ICONS.arrowUp, 11));
  const bar = h("div", { class: "chat-bar" }, input, send);

  const modelDot = h("i", { class: "model-dot" });
  const modelName = h("span", { class: "model-name" });
  const modelBtn = h(
    "button",
    { class: "model-btn", title: tl(STRINGS.switchModel) },
    modelDot,
    modelName,
    svg(ICONS.chevronUpDown, 9, { stroke: 2 }),
  );
  const modelRow = h("div", { class: "model-row" }, modelBtn);

  // Model token & context usage section
  const usageModelName = h("span", { class: "chat-usage-model" });
  const usageTokens = h("span", { class: "chat-usage-tokens" });
  const usageBadge = h("span", { class: "chat-usage-badge" });

  const usageBox = document.createElement("div");
  usageBox.className = "chat-usage-meter-box";
  const usageSvg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  usageSvg.setAttribute("viewBox", "0 0 100 4");
  usageSvg.setAttribute("preserveAspectRatio", "none");
  usageSvg.setAttribute("class", "chat-usage-svg");

  const usageTrack = document.createElementNS("http://www.w3.org/2000/svg", "rect");
  usageTrack.setAttribute("x", "0");
  usageTrack.setAttribute("y", "0");
  usageTrack.setAttribute("width", "100");
  usageTrack.setAttribute("height", "4");
  usageTrack.setAttribute("rx", "2");
  usageTrack.setAttribute("class", "chat-usage-track");

  const usageFill = document.createElementNS("http://www.w3.org/2000/svg", "rect");
  usageFill.setAttribute("x", "0");
  usageFill.setAttribute("y", "0");
  usageFill.setAttribute("width", "0");
  usageFill.setAttribute("height", "4");
  usageFill.setAttribute("rx", "2");
  usageFill.setAttribute("class", "chat-usage-fill");

  usageSvg.append(usageTrack, usageFill);
  usageBox.append(usageSvg);

  const usageBar = h(
    "div",
    { class: "chat-usage-bar" },
    h(
      "div",
      { class: "chat-usage-header" },
      h("div", { class: "chat-usage-left" }, usageModelName, usageTokens),
      usageBadge,
    ),
    usageBox,
  );

  function updateUsageDisplay() {
    const usage = getCurrentModelUsage();
    const currentModel = usage.model || activeModel(State.settings);
    usageModelName.textContent = currentModel;
    usageTokens.textContent = `Used: ${formatTokens(usage.totalTokens)} / ${formatTokens(usage.contextLimit)}`;
    usageBadge.textContent = `${usage.remainingPercentage.toFixed(1)}% rem (${formatTokens(usage.remainingTokens)})`;
    usageBadge.title = `${usage.remainingTokens.toLocaleString()} tokens remaining (${usage.remainingPercentage.toFixed(2)}%)`;

    usageBadge.classList.toggle("warn", usage.remainingPercentage <= 30 && usage.remainingPercentage > 10);
    usageBadge.classList.toggle("crit", usage.remainingPercentage <= 10);
    usageFill.classList.toggle("warn", usage.remainingPercentage <= 30 && usage.remainingPercentage > 10);
    usageFill.classList.toggle("crit", usage.remainingPercentage <= 10);

    const usedPercent = usage.contextLimit > 0
      ? (usage.totalTokens / usage.contextLimit) * 100
      : 0;
    const clamped = Math.max(0, Math.min(100, usedPercent));
    usageFill.setAttribute("width", clamped.toFixed(1));
  }

  const body = h("div", { class: "chat-body" });
  const picker = buildPicker(() => {
    body.classList.toggle("picking", picker.isOpen);
    drawModelButton();
    updateUsageDisplay();
  });
  body.append(chipRow, log, picker.el, modelRow, usageBar, bar);
  void loadSubscriptions().then(() => drawModelButton());

  const el = h("div", { class: "view" }, h("div", { class: "card wash chat-card" }, body));

  let sending = false;
  let renderedCount = -1;
  let live: HTMLElement | null = null;

  function drawModelButton() {
    const p = providerDef(State.settings.chatProvider);
    modelDot.className = `model-dot provider-${p.id}`;
    modelName.textContent = chipLabel(State.settings, activeModel(State.settings)) || t(STRINGS.noModel);
    modelBtn.classList.toggle("open", picker.isOpen);
    modelBtn.disabled = sending;
  }

  modelBtn.addEventListener("click", () => {
    if (sending) return;
    if (picker.isOpen) picker.close();
    else picker.open();
  });

  void onEvent<string>("chat-delta", (text) => {
    if (!sending || !text) return;
    if (!live) {
      live = h("div", { class: "reply" });
      log.querySelector(".typing")?.parentElement?.remove();
      log.append(h("div", { class: "chat-row" }, live));
    }
    renderMarkdown(live, text);
    log.scrollTop = log.scrollHeight;
  });

  async function submit() {
    const query = input.value.trim();
    if (!query || sending) return;
    if (picker.isOpen) picker.close();
    input.value = "";
    sending = true;
    drawModelButton();
    Sound.play("send");

    State.chatHistory.push({ id: nextId++, role: "user", content: query });
    State.stateOverride = "thinking";
    State.notify();
    onHeightChange();

    const file = State.droppedFile;
    const context: ChatContext | null =
      State.chatHistory.length === 1 && file ? { kind: "file", name: file.name, path: file.path } : null;

    try {
      const reply = await sendChatMessage(query, context);
      State.chatHistory.push({ id: nextId++, role: "assistant", content: reply.text });
      State.stateOverride = null;
      Sound.play("finish");
      updateUsageDisplay();
    } catch (err) {
      State.stateOverride = null;
      State.noteMessage = String(err).replace(/^Error:\s*/, "");
      State.view = "note";
      Sound.play("error");
    } finally {
      sending = false;
      live = null;
      renderedCount = -1;
      drawModelButton();
      updateUsageDisplay();
      State.notify();
      onHeightChange();
      // Back to typing without a click. The field was disabled while the
      // answer came, which took the focus away, and the sync that enables it
      // again is a frame away: a disabled field can't take the focus.
      if (State.view === "prompt") {
        input.disabled = false;
        input.focus();
      }
    }
  }

  send.addEventListener("click", () => void submit());
  input.addEventListener("keydown", (e) => {
    const key = (e as KeyboardEvent).key;
    if (key === "Enter") {
      e.preventDefault();
      void submit();
    } else if (key === "Escape" && picker.isOpen) {
      e.preventDefault();
      picker.close();
    }
    e.stopPropagation();
  });

  return {
    el,
    sync() {
      const file = State.droppedFile;
      const wantChip = file?.name ?? "";
      if (chipRow.dataset.label !== wantChip) {
        chipRow.dataset.label = wantChip;
        clear(chipRow);
        if (wantChip) chipRow.append(contextChip(wantChip));
      }

      const thinking = State.stateOverride === "thinking";
      const count = State.chatHistory.length + (thinking ? 0.5 : 0);
      if (count !== renderedCount && !live) {
        renderedCount = count;
        clear(log);
        for (const m of State.chatHistory) log.append(bubble(m));
        if (thinking) log.append(typingDots());
        log.scrollTop = log.scrollHeight;
      }

      if (State.view !== "prompt" && picker.isOpen) picker.close();
      drawModelButton();
      updateUsageDisplay();

      input.placeholder = t(State.chatHistory.length === 0 ? STRINGS.placeholderFirst : STRINGS.placeholderNext);
      input.disabled = sending;
    },
    focus() {
      input.focus();
      input.select();
    },
  };
}
