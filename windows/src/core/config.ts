// Configuration mapped from .env

export const ANTHROPIC_BASE_URL =
  import.meta.env.VITE_ANTHROPIC_BASE_URL || "https://api.anthropic.com/v1/messages";

export const GEMINI_BASE_URL =
  import.meta.env.VITE_GEMINI_BASE_URL || "https://generativelanguage.googleapis.com/v1beta/openai";
