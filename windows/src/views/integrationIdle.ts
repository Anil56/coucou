import { h, dot } from "./dom";
import { State, type AgentTask } from "../core/state";
import { Bridge } from "../core/bridge";
import { isComingSoon, pillDefinition } from "../core/pills";
import { refreshHookPills } from "../island/integrations";
import { N_, t } from "../i18n/i18n";

const OPEN_URLS: Record<string, string> = {
  integration_resend: "https://resend.com/emails",
  integration_vercel: "https://vercel.com/dashboard",
  integration_github: "https://github.com",
  integration_stripe: "https://dashboard.stripe.com/payments",
  integration_notion: "https://notion.so",
  integration_calcom: "https://app.cal.com/bookings",
};

export function idleStatus(
  id: string,
  info: { configured: boolean; error: string | null } | undefined,
  chatModel: string,
): { label: string; color: string } {
  if (isComingSoon(id)) return { label: t("Coming soon"), color: "#6B7079" };
  if (info?.error) return { label: info.error, color: "#F4505E" };
  const configured = info?.configured ?? false;
  const def = pillDefinition(id);
  const ok = (label: string) => ({ label, color: "#22C55E" });
  const missing = (label: string) => ({ label, color: "#F4505E" });

  if (def?.connect.kind === "hooks") return configured ? ok(t("Hooks installed")) : missing(t("Hooks not installed"));
  if (def?.connect.kind === "none") return ok(t("Ready · no setup needed"));
  if (def?.connect.kind === "server") return configured ? ok(t("Connected")) : missing(t("Not connected"));
  if (def?.category === "ai") {
    if (!configured) return missing(t("Key not configured"));
    return ok(id === "ai_anthropic" ? t("Key configured · {model}", { model: chatModel }) : t("Key configured"));
  }
  return configured ? ok(t("Connected · loading…")) : missing(t("Key not configured"));
}

export function idleCard(
  task: AgentTask,
  openSettings: () => void,
  headerFn: (color: string, name: string, kind: string, extra?: Node) => HTMLElement,
): HTMLElement {
  const isAgent = task.source === "agent" || !task.isIntegration || task.id.startsWith("agent_");
  const info = State.integrations[task.id];
  const configured = isAgent ? true : (info?.configured ?? false);
  const def = pillDefinition(task.id);
  const status = isAgent
    ? {
        label: task.steps.length > 0 ? task.steps[task.steps.length - 1] : t("Ready · waiting for session"),
        color: "#22C55E",
      }
    : idleStatus(task.id, info, State.settings.model);

  const actions = h("div", { class: "int-actions" });
  if (isAgent) {
    actions.append(
      h("button", {
        class: "link-btn",
        text: t("Open Chat"),
        onclick: () => {
          State.view = "prompt";
          State.notify();
        },
      }),
    );
  } else if (task.id === "agent_claude-desktop") {
    actions.append(
      h("button", {
        class: "link-btn",
        text: t("Open Claude"),
        onclick: () => void Bridge.openClaudeDesktop(),
      }),
    );
  } else if (task.id === "integration_n8n") {
    actions.append(
      h("button", {
        class: "link-btn",
        text: t("Open {name}", { name: "n8n" }),
        onclick: () => void Bridge.openN8n(),
      }),
    );
  } else if (OPEN_URLS[task.id]) {
    actions.append(
      h("button", {
        class: "link-btn",
        text: t("Open {name}", { name: task.name }),
        onclick: () => void Bridge.openUrl(OPEN_URLS[task.id]),
      }),
    );
  }

  const hookPill = def?.connect.kind === "hooks";
  if (!isAgent) {
    if (isComingSoon(task.id) || def?.connect.kind === "none") {
      // Nothing to set up or refresh
    } else if (configured && (hookPill || def?.category !== "ai")) {
      actions.append(
        h("button", {
          class: "link-btn",
          text: t("Refresh"),
          onclick: () => void (hookPill ? refreshHookPills() : Bridge.refreshIntegration(task.id)),
        }),
      );
    } else if (!configured) {
      actions.append(
        h("button", { class: "link-btn dim", text: t("Settings…"), onclick: openSettings }),
      );
    }
  }

  const kindLabel = isAgent ? t("Agent") : t(def?.subtitle ?? N_("Integration"));

  return h(
    "div",
    { class: "int-card" },
    headerFn(
      task.color,
      task.id === "integration_claude" ? "VS Code" : task.name,
      kindLabel,
    ),
    h("div", { class: "int-status" }, dot(status.color, 5), h("span", { text: status.label })),
    actions,
  );
}
