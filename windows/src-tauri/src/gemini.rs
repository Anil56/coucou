#![allow(dead_code)]

// Gemini & Antigravity provider client.
// Supports both direct Gemini API (via google-api-key / gemini-api-key)
// and Antigravity CLI (`agy`) sessions.

use serde_json::{json, Value};
use std::process::Command;

use crate::chat::{calculate_usage, ChatContext, ChatReply};
use crate::platform;
use crate::secrets;

pub const DEFAULT_MODEL: &str = "gemini-3.8-flash-high";

const SYSTEM_PROMPT: &str = "You are Mochi, a personal AI assistant living at the top of the user's screen. \
You can help with absolutely anything — research, coding, finding places, recommendations, tasks, questions. \
Respond in the user's language. Be thorough and complete — use as much detail as the task requires. \
No markdown formatting (no **, no ##, no bullet dashes). Use plain text with line breaks.";

pub async fn send(
    model: &str,
    query: String,
    context: Option<ChatContext>,
) -> Result<ChatReply, String> {
    let key = secrets::get("gemini-api-key")
        .or_else(|| secrets::get("google-api-key"))
        .or_else(|| std::env::var("GEMINI_API_KEY").ok());

    let mut full_prompt = String::new();
    if let Some(ctx) = &context {
        match ctx {
            ChatContext::File { name, path } => {
                if let Ok(content) = std::fs::read_to_string(path) {
                    let truncated = if content.len() > 100_000 {
                        format!("{}\n[truncated]", &content[..100_000])
                    } else {
                        content
                    };
                    full_prompt.push_str(&format!("File: {name}\nContents:\n{truncated}\n\n"));
                } else {
                    full_prompt.push_str(&format!("File: {name}\n\n"));
                }
            }
            ChatContext::Window { app_name, title, url } => {
                let mut info = format!("Context — App: {app_name}, Window: {title}");
                if let Some(u) = url {
                    info.push_str(&format!(", URL: {u}"));
                }
                full_prompt.push_str(&format!("{info}\n\n"));
            }
        }
    }
    full_prompt.push_str(&query);

    if let Some(api_key) = key {
        match call_gemini_api(&api_key, model, &full_prompt).await {
            Ok(reply) => return Ok(reply),
            Err(e) => {
                crate::log::line(format!("Gemini API error ({e}), attempting agy CLI fallback"));
            }
        }
    }

    // Fallback or default to Antigravity CLI (`agy`)
    call_agy_cli(model, &full_prompt).await
}

async fn call_gemini_api(key: &str, model: &str, prompt: &str) -> Result<ChatReply, String> {
    let base_url = crate::config::gemini_base_url();
    let url = if base_url.ends_with("/chat/completions") {
        base_url
    } else {
        format!("{}/chat/completions", base_url.trim_end_matches('/'))
    };

    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(90))
        .build()
        .map_err(|e| e.to_string())?;

    let body = json!({
        "model": model,
        "max_tokens": 4096,
        "messages": [
            { "role": "system", "content": SYSTEM_PROMPT },
            { "role": "user", "content": prompt }
        ]
    });

    let res = client
        .post(&url)
        .header("Authorization", format!("Bearer {key}"))
        .header("Content-Type", "application/json")
        .json(&body)
        .send()
        .await
        .map_err(|e| format!("Network error: {e}"))?;

    let status = res.status();
    let text = res.text().await.map_err(|e| e.to_string())?;
    if !status.is_success() {
        let detail = serde_json::from_str::<Value>(&text)
            .ok()
            .and_then(|v| {
                v.get("error")
                    .and_then(|e| e.get("message"))
                    .and_then(Value::as_str)
                    .map(str::to_string)
            })
            .unwrap_or_else(|| text.chars().take(200).collect());
        return Err(format!("Gemini API {status}: {detail}"));
    }

    let parsed: Value = serde_json::from_str(&text).map_err(|e| format!("Bad JSON response: {e}"))?;
    let content = parsed
        .get("choices")
        .and_then(Value::as_array)
        .and_then(|c| c.first())
        .and_then(|c| c.get("message"))
        .and_then(|m| m.get("content"))
        .and_then(Value::as_str)
        .unwrap_or("")
        .trim()
        .to_string();

    if content.is_empty() {
        return Err("No response received from Gemini.".into());
    }

    let usage = parsed.get("usage").map(|u| {
        let in_tok = u.get("prompt_tokens").and_then(Value::as_u64).unwrap_or(0);
        let out_tok = u.get("completion_tokens").and_then(Value::as_u64).unwrap_or(0);
        calculate_usage(model, in_tok, out_tok)
    }).or_else(|| {
        parsed.get("usageMetadata").map(|u| {
            let in_tok = u.get("promptTokenCount").and_then(Value::as_u64).unwrap_or(0);
            let out_tok = u.get("candidatesTokenCount").and_then(Value::as_u64).unwrap_or(0);
            calculate_usage(model, in_tok, out_tok)
        })
    });

    Ok(ChatReply {
        text: content,
        usage,
        model: Some(model.to_string()),
    })
}

async fn call_agy_cli(model: &str, prompt: &str) -> Result<ChatReply, String> {
    let agy_bin = platform::find_on_path("agy").or_else(|| {
        let local = platform::home_dir().join(".local").join("bin").join("agy");
        if local.exists() {
            Some(local)
        } else {
            None
        }
    });

    let agy = agy_bin.ok_or_else(|| {
        "Antigravity CLI (agy) not found and no Gemini API key configured. Open Settings.".to_string()
    })?;

    let model_str = model.to_string();
    let prompt_str = prompt.to_string();

    tokio::task::spawn_blocking(move || {
        let mut cmd = Command::new(agy);
        if !model_str.is_empty() {
            cmd.arg("--model").arg(&model_str);
        }
        cmd.arg("--output-format").arg("json");
        cmd.arg("-p").arg(&prompt_str);

        let output = platform::no_console(&mut cmd)
            .output()
            .map_err(|e| format!("Failed to run agy: {e}"))?;

        if !output.status.success() {
            let err = String::from_utf8_lossy(&output.stderr);
            let out = String::from_utf8_lossy(&output.stdout);
            let msg = if !err.trim().is_empty() { err } else { out };
            return Err(format!("Antigravity error: {}", msg.trim()));
        }

        let raw_stdout = String::from_utf8_lossy(&output.stdout);
        let (reply, usage) = if let Ok(parsed) = serde_json::from_str::<Value>(&raw_stdout) {
            let text = parsed
                .get("response")
                .and_then(Value::as_str)
                .unwrap_or(&raw_stdout)
                .trim()
                .to_string();
            let u = parsed.get("usage").map(|us| {
                let in_tok = us.get("input_tokens").and_then(Value::as_u64).unwrap_or(0);
                let out_tok = us.get("output_tokens").and_then(Value::as_u64).unwrap_or(0);
                calculate_usage(&model_str, in_tok, out_tok)
            });
            (text, u)
        } else {
            (raw_stdout.trim().to_string(), None)
        };

        if reply.is_empty() {
            return Err("Antigravity returned an empty response.".into());
        }
        Ok(ChatReply {
            text: reply,
            usage,
            model: Some(model_str),
        })
    })
    .await
    .map_err(|e| format!("Task execution error: {e}"))?
}
