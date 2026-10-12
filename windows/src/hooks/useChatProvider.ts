// Chat provider and model management hook.
// Supports Claude (Anthropic), Google / Antigravity, and other configured providers.

import { Bridge, type ChatContext, type ChatReply } from "../core/bridge";
import { State, type TokenUsage } from "../core/state";
import { activeModel, withModel, type ProviderId } from "../core/providers";

export type ChatProviderId = ProviderId;

export const CLAUDE_MODELS: [string, string][] = [
  ["claude-opus-5", "Claude Opus 5"],
  ["claude-sonnet-5", "Claude Sonnet 5"],
  ["claude-haiku-4-5", "Claude Haiku 4.5"],
];

export const GEMINI_MODELS: [string, string][] = [
  ["gemini-2.5-pro", "Gemini 2.5 Pro"],
  ["gemini-2.5-flash", "Gemini 2.5 Flash"],
  ["gemini-2.0-flash", "Gemini 2.0 Flash"],
];

export function getModelContextLimit(model: string): number {
  const m = model.toLowerCase();
  if (m.includes("pro")) return 2_000_000;
  if (m.includes("gemini")) return 1_000_000;
  if (m.includes("claude")) return 200_000;
  return 200_000;
}

export function formatTokens(num: number): string {
  if (num >= 1_000_000) {
    const formatted = (num / 1_000_000).toFixed(1).replace(/\.0$/, "");
    return `${formatted}M`;
  }
  if (num >= 1_000) {
    const formatted = (num / 1_000).toFixed(1).replace(/\.0$/, "");
    return `${formatted}k`;
  }
  return num.toLocaleString();
}

export function getActiveChatProvider(): ProviderId {
  return State.settings.chatProvider;
}

export function setActiveChatProvider(provider: ProviderId): void {
  State.settings = { ...State.settings, chatProvider: provider };
  void Bridge.saveSettings(State.settings);
  State.notify();
}

export function getActiveModel(): string {
  return activeModel(State.settings);
}

export function setActiveModel(model: string): void {
  State.settings = withModel(State.settings, State.settings.chatProvider, model);
  void Bridge.saveSettings(State.settings);
  State.notify();
}

export function getCurrentModelUsage(): TokenUsage {
  const model = getActiveModel();
  if (State.modelUsage && State.modelUsage.model === model) {
    return State.modelUsage;
  }
  const limit = getModelContextLimit(model);
  return {
    inputTokens: 0,
    outputTokens: 0,
    totalTokens: 0,
    contextLimit: limit,
    remainingTokens: limit,
    remainingPercentage: 100,
    model,
  };
}

export function updateModelUsage(usage: TokenUsage | null): void {
  State.modelUsage = usage;
  State.notify();
}

export async function sendChatMessage(
  query: string,
  context: ChatContext | null,
): Promise<ChatReply> {
  const provider = getActiveChatProvider();
  const model = getActiveModel();
  const reply = await Bridge.chatSend(query, context, provider, model);
  if (reply.usage) {
    updateModelUsage(reply.usage);
  } else {
    const current = getCurrentModelUsage();
    const estInput = current.totalTokens + Math.ceil(query.length / 4);
    const estOutput = Math.ceil((reply.text?.length || 0) / 4);
    const total = estInput + estOutput;
    const limit = getModelContextLimit(model);
    const remaining = Math.max(0, limit - total);
    const remainingPct = limit > 0 ? (remaining / limit) * 100 : 0;
    updateModelUsage({
      inputTokens: estInput,
      outputTokens: estOutput,
      totalTokens: total,
      contextLimit: limit,
      remainingTokens: remaining,
      remainingPercentage: remainingPct,
      model,
    });
  }
  return reply;
}
