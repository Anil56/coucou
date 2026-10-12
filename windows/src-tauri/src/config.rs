// Configuration mapped from environment / .env

pub fn anthropic_base_url() -> String {
    std::env::var("ANTHROPIC_BASE_URL")
        .unwrap_or_else(|_| "https://api.anthropic.com/v1/messages".to_string())
}

pub fn gemini_base_url() -> String {
    std::env::var("GEMINI_BASE_URL")
        .unwrap_or_else(|_| "https://generativelanguage.googleapis.com/v1beta/openai".to_string())
}
