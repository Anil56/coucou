use super::*;
use serde_json::{json, Value};

/// A settings.json in which no value is the default one.
// Two #: the colours in it are written "#RRGGBB".
const CUSTOM: &str = r##"{
  "soundEnabled": false,
  "soundVolume": 0.5,
  "autoCloseInterval": 30.0,
  "openOnHover": true,
  "absenceInterval": 60.0,
  "activeIntegrations": ["integration_notion"],
  "mainPill": "agent_cursor",
  "screen": "cursor",
  "autostart": true,
  "hooksInstalled": true,
  "model": "some-model",
  "showPlanInNotch": true,
  "planRelayInstalled": true,
  "showCodexPlanInNotch": true,
  "chatProvider": "ollama",
  "chatModels": { "ollama": "llama3.2", "openai": "gpt-x" },
  "ollamaUrl": "http://127.0.0.1:11434",
  "lmstudioUrl": "http://127.0.0.1:1234",
  "customUrl": "https://llm.example.com",
  "shortcuts": { "openChat": { "keys": "Ctrl+Shift+K", "enabled": false } },
  "mochiOutfit": "witchHat",
  "pillColors": { "integration_claude": "#2DD4BF" },
  "language": "pt-BR",
  "desktopMochi": { "onDesktop": true, "spot": { "x": 1500.5, "y": -300.0, "space": "screen" } },
  "phoneLink": true,
  "phoneChat": true,
  "phoneChatModels": ["openai/gpt-x"],
  "phoneDetails": true,
  "phoneAnswers": true,
  "phoneDiffs": true,
  "phoneUsage": true,
  "phoneRecap": true,
  "phoneServices": ["integration_stripe"],
  "phoneRelay": true,
  "phoneRelayUrl": "wss://relay.example.workers.dev",
  "showClock": true,
  "minimizeOnHoverAway": false,
  "hoverAwayDelay": 2.5
}"##;

fn custom() -> Value {
    serde_json::from_str(CUSTOM).unwrap()
}

/// `CUSTOM` with one key replaced, or removed when `value` is `None`.
fn custom_with(key: &str, value: Option<Value>) -> Vec<u8> {
    let mut object = custom().as_object().unwrap().clone();
    match value {
        Some(value) => object.insert(key.to_string(), value),
        None => object.remove(key),
    };
    serde_json::to_vec(&object).unwrap()
}

/// Settings compare through their JSON: the struct has no `PartialEq`.
fn shown(settings: &Settings) -> Value {
    serde_json::to_value(settings).unwrap()
}

fn defaults() -> Value {
    shown(&Settings::default())
}

/// A fresh directory of our own, and the settings.json it will hold.
fn scratch(name: &str) -> (PathBuf, PathBuf) {
    let dir = std::env::temp_dir()
        .join(format!("coucou-settings-{name}-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir_all(&dir).unwrap();
    let file = dir.join("settings.json");
    (dir, file)
}

fn names(dir: &Path) -> Vec<String> {
    let mut names: Vec<String> = std::fs::read_dir(dir)
        .unwrap()
        .map(|entry| entry.unwrap().file_name().to_string_lossy().to_string())
        .collect();
    names.sort();
    names
}

/// What was set aside under `settings.json.<kind>-…`.
fn kept(dir: &Path, kind: &str) -> Vec<Vec<u8>> {
    let prefix = format!("settings.json.{kind}-");
    names(dir)
        .iter()
        .filter(|name| name.starts_with(&prefix))
        .map(|name| std::fs::read(dir.join(name)).unwrap())
        .collect()
}

// ── Reading ───────────────────────────────────────────────────────────────

#[test]
fn a_missing_file_is_the_defaults_and_is_not_created() {
    let (dir, file) = scratch("missing");
    assert_eq!(shown(&load_from(&file)), defaults());
    assert!(names(&dir).is_empty());
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn an_empty_file_is_the_defaults_and_nothing_is_set_aside() {
    let (dir, file) = scratch("empty");
    for nothing in [&b""[..], &b" \r\n\t "[..]] {
        std::fs::write(&file, nothing).unwrap();
        assert_eq!(shown(&load_from(&file)), defaults());
    }
    assert_eq!(names(&dir), ["settings.json"]);
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn unknown_fields_are_ignored_and_the_known_ones_kept() {
    let bytes = custom_with("somethingFromALaterVersion", Some(json!({ "a": [1, 2] })));
    assert_eq!(shown(&parse(&bytes).unwrap()), custom());
}

#[test]
fn a_file_from_before_the_model_setting_keeps_everything_else() {
    let loaded = shown(&parse(&custom_with("model", None)).unwrap());
    let mut expected = custom();
    expected["model"] = json!(crate::claude::DEFAULT_MODEL);
    assert_eq!(loaded, expected);
}

#[test]
fn a_file_from_before_the_wardrobe_dresses_mochi_for_the_seasons() {
    let loaded = parse(&custom_with("mochiOutfit", None)).unwrap();
    assert_eq!(loaded.mochi_outfit, "auto");
    assert_eq!(loaded.model, "some-model");
    assert!(!loaded.sound_enabled);
}

#[test]
fn a_file_from_before_the_desktop_mochi_keeps_him_in_the_island() {
    let loaded = parse(&custom_with("desktopMochi", None)).unwrap();
    assert_eq!(loaded.desktop_mochi, DesktopMochiPref::default());
    assert!(!loaded.desktop_mochi.on_desktop);
    assert_eq!(loaded.mochi_outfit, "witchHat");
}

#[test]
fn a_file_from_before_the_phone_link_keeps_it_off() {
    // The link opens a port on the local network: it is never on by default,
    // and an older file must not be read as consent.
    assert!(!Settings::default().phone_link);
    let loaded = parse(&custom_with("phoneLink", None)).unwrap();
    assert!(!loaded.phone_link);
    assert_eq!(loaded.mochi_outfit, "witchHat");
    assert!(parse(&custom_with("phoneLink", Some(serde_json::json!(true)))).unwrap().phone_link);

    // A value of the wrong type does not switch it on either, and costs nothing else.
    let (dir, file) = scratch("phone-link");
    std::fs::write(&file, custom_with("phoneLink", Some(serde_json::json!("yes")))).unwrap();
    let odd = load_from(&file);
    assert!(!odd.phone_link);
    assert_eq!(odd.mochi_outfit, "witchHat");
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn a_file_from_before_phone_chat_keeps_it_off_and_empty() {
    // Chat spends the user's API credit: never on by default, and an older file is not consent.
    let d = Settings::default();
    assert!(!d.phone_chat);
    assert!(d.phone_chat_models.is_empty());
    let loaded = parse(&custom_with("phoneChat", None)).unwrap();
    assert!(!loaded.phone_chat);
    assert!(parse(&custom_with("phoneChat", Some(serde_json::json!(true)))).unwrap().phone_chat);
    let odd = parse(&custom_with("phoneChat", Some(serde_json::json!("yes")))).unwrap();
    assert!(!odd.phone_chat, "a wrong type does not switch it on");
    assert_eq!(odd.mochi_outfit, "witchHat");
    let models = parse(&custom_with("phoneChatModels", Some(serde_json::json!(["openai/gpt-x"])))).unwrap();
    assert_eq!(models.phone_chat_models, vec!["openai/gpt-x".to_string()]);
    let bad = parse(&custom_with("phoneChatModels", Some(serde_json::json!("openai/gpt-x")))).unwrap();
    assert!(bad.phone_chat_models.is_empty());
}

#[test]
fn a_file_from_before_phone_answers_keeps_it_off() {
    // Answering for the agent from the phone is never on by default; an older file is not consent.
    assert!(!Settings::default().phone_answers);
    assert!(!parse(&custom_with("phoneAnswers", None)).unwrap().phone_answers);
    assert!(parse(&custom_with("phoneAnswers", Some(serde_json::json!(true)))).unwrap().phone_answers);
    assert!(!parse(&custom_with("phoneAnswers", Some(serde_json::json!("yes")))).unwrap().phone_answers, "a wrong type does not switch it on");
}

#[test]
fn a_file_from_before_phone_diffs_keeps_it_off() {
    // Showing what an agent changed on the phone is never on by default; an older file is not consent.
    assert!(!Settings::default().phone_diffs);
    assert!(!parse(&custom_with("phoneDiffs", None)).unwrap().phone_diffs);
    assert!(parse(&custom_with("phoneDiffs", Some(serde_json::json!(true)))).unwrap().phone_diffs);
    assert!(!parse(&custom_with("phoneDiffs", Some(serde_json::json!("yes")))).unwrap().phone_diffs, "a wrong type does not switch it on");
}

#[test]
fn a_file_from_before_phone_usage_keeps_it_off() {
    assert!(!Settings::default().phone_usage);
    assert!(!parse(&custom_with("phoneUsage", None)).unwrap().phone_usage);
    assert!(parse(&custom_with("phoneUsage", Some(serde_json::json!(true)))).unwrap().phone_usage);
    assert!(!parse(&custom_with("phoneUsage", Some(serde_json::json!("yes")))).unwrap().phone_usage, "a wrong type does not switch it on");
}

#[test]
fn a_file_from_before_the_weekly_recap_on_the_phone_keeps_it_off() {
    // Sending last week's numbers to the phone is never on by default; an older file is not consent.
    assert!(!Settings::default().phone_recap);
    assert!(!parse(&custom_with("phoneRecap", None)).unwrap().phone_recap);
    assert!(parse(&custom_with("phoneRecap", Some(serde_json::json!(true)))).unwrap().phone_recap);
    assert!(!parse(&custom_with("phoneRecap", Some(serde_json::json!("yes")))).unwrap().phone_recap, "a wrong type does not switch it on");
}

#[test]
fn a_file_from_before_the_relay_keeps_it_off_and_empty() {
    assert!(!Settings::default().phone_relay);
    assert!(Settings::default().phone_relay_url.is_empty());
    let old = parse(&custom_with("phoneRelay", None)).unwrap();
    assert!(!old.phone_relay);
    assert!(parse(&custom_with("phoneRelay", Some(serde_json::json!(true)))).unwrap().phone_relay);
    assert!(!parse(&custom_with("phoneRelay", Some(serde_json::json!("yes")))).unwrap().phone_relay, "a wrong type does not switch it on");
    assert!(parse(&custom_with("phoneRelayUrl", Some(serde_json::json!(5)))).unwrap().phone_relay_url.is_empty());
}

#[test]
fn a_file_from_before_phone_services_shows_none() {
    // Showing a service on the phone is never on by default; an older file is not consent.
    assert!(Settings::default().phone_services.is_empty());
    assert!(parse(&custom_with("phoneServices", None)).unwrap().phone_services.is_empty());
    assert_eq!(parse(&custom_with("phoneServices", Some(serde_json::json!(["integration_stripe"])))).unwrap().phone_services, vec!["integration_stripe".to_string()]);
    assert!(parse(&custom_with("phoneServices", Some(serde_json::json!("integration_stripe")))).unwrap().phone_services.is_empty(), "a wrong type shows none");
}

#[test]
fn a_file_from_before_phone_details_keeps_them_off() {
    // Details can include commands and folder names: never on by default, an older file is not consent.
    assert!(!Settings::default().phone_details);
    assert!(!parse(&custom_with("phoneDetails", None)).unwrap().phone_details);
    assert!(parse(&custom_with("phoneDetails", Some(serde_json::json!(true)))).unwrap().phone_details);
    let odd = parse(&custom_with("phoneDetails", Some(serde_json::json!("yes")))).unwrap();
    assert!(!odd.phone_details, "a wrong type does not switch it on");
    assert_eq!(odd.mochi_outfit, "witchHat");
}

#[test]
fn a_half_written_desktop_spot_costs_only_the_spot() {
    let loaded = parse(&custom_with(
        "desktopMochi",
        Some(json!({ "onDesktop": true, "spot": { "x": "left" } })),
    ))
    .unwrap();
    // The whole field falls back, and nothing else does.
    assert_eq!(loaded.desktop_mochi, DesktopMochiPref::default());
    assert_eq!(loaded.model, "some-model");

    let loaded = parse(&custom_with("desktopMochi", Some(json!({ "onDesktop": true })))).unwrap();
    assert!(loaded.desktop_mochi.on_desktop);
    assert_eq!(loaded.desktop_mochi.spot, None);
}

#[test]
fn a_file_from_before_the_colours_paints_every_pill_as_the_catalog_says() {
    let loaded = parse(&custom_with("pillColors", None)).unwrap();
    assert!(loaded.pill_colors.is_empty());
    assert_eq!(loaded.mochi_outfit, "witchHat");
}

#[test]
fn pill_colours_are_kept_as_written_and_cost_nothing_else_when_unusable() {
    // A pill a newer build added keeps its colour through a save by this one.
    let loaded = parse(&custom_with("pillColors", Some(json!({ "agent_new": "#abcdef" })))).unwrap();
    assert_eq!(loaded.pill_colors.get("agent_new").map(String::as_str), Some("#abcdef"));
    // Not a map of strings: the colours fall back, and nothing else does.
    let loaded = parse(&custom_with("pillColors", Some(json!(["#2DD4BF"])))).unwrap();
    assert!(loaded.pill_colors.is_empty());
    assert_eq!(loaded.model, "some-model");
}

#[test]
fn an_outfit_this_build_does_not_know_is_kept_as_written() {
    // A newer build may add outfits: the island shows "auto" for it, but
    // the choice must survive a save made by this one.
    let loaded = parse(&custom_with("mochiOutfit", Some(json!("topHat")))).unwrap();
    assert_eq!(loaded.mochi_outfit, "topHat");
    // The language too: "" (follow the system) when absent, as it comes otherwise.
    assert_eq!(parse(&custom_with("language", None)).unwrap().language, "");
    assert_eq!(parse(&custom_with("language", Some(json!("xx")))).unwrap().language, "xx");
}

#[test]
fn a_missing_field_takes_its_default_and_the_rest_are_kept() {
    for key in custom().as_object().unwrap().keys() {
        let loaded = parse(&custom_with(key, None))
            .unwrap_or_else(|| panic!("a file without {key} was thrown away"));
        let mut expected = custom();
        expected[key] = defaults()[key].clone();
        assert_eq!(shown(&loaded), expected, "without {key}");
    }
}

#[test]
fn a_field_of_the_wrong_type_takes_its_default_and_the_rest_are_kept() {
    for (key, wrong) in [
        ("soundVolume", json!("loud")),
        ("soundEnabled", json!(1)),
        ("activeIntegrations", json!("integration_n8n")),
        ("mainPill", json!(["agent_cursor"])),
        ("screen", Value::Null),
        ("shortcuts", json!("Ctrl+Alt+A")),
    ] {
        let loaded = parse(&custom_with(key, Some(wrong)))
            .unwrap_or_else(|| panic!("a file with a bad {key} was thrown away"));
        let mut expected = custom();
        expected[key] = defaults()[key].clone();
        assert_eq!(shown(&loaded), expected, "bad {key}");
    }
}

#[test]
fn a_utf8_bom_is_not_corruption() {
    // What PowerShell 5's `Set-Content -Encoding utf8` and Notepad write.
    let (dir, file) = scratch("bom");
    let mut bytes = vec![0xEF, 0xBB, 0xBF];
    bytes.extend_from_slice(CUSTOM.as_bytes());
    std::fs::write(&file, &bytes).unwrap();
    assert_eq!(shown(&load_from(&file)), custom());
    assert_eq!(names(&dir), ["settings.json"]);
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn an_unusable_file_is_the_defaults_and_is_set_aside_byte_for_byte() {
    for (name, bad) in [
        ("invalid", &b"{ \"soundVolume\": 0.5, \xff oops"[..]),
        ("not-an-object", &b"[1, 2, 3]"[..]),
    ] {
        let (dir, file) = scratch(name);
        std::fs::write(&file, bad).unwrap();
        assert_eq!(shown(&load_from(&file)), defaults());
        assert_eq!(kept(&dir, "corrupt"), [bad.to_vec()], "{name}");
        let _ = std::fs::remove_dir_all(&dir);
    }
}

#[test]
fn a_second_unusable_file_never_replaces_the_copy_of_the_first() {
    // Both within the same second: the names must still differ.
    let (dir, file) = scratch("twice");
    let (first, second) = (&b"{ first"[..], &b"{ second"[..]);
    std::fs::write(&file, first).unwrap();
    load_from(&file);
    std::fs::write(&file, second).unwrap();
    load_from(&file);
    let mut copies = kept(&dir, "corrupt");
    copies.sort();
    assert_eq!(copies, [first.to_vec(), second.to_vec()]);
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn something_that_is_not_a_readable_file_is_the_defaults_and_is_left_alone() {
    // A directory in the file's place: a read error that is not "not found".
    let (dir, file) = scratch("unreadable");
    std::fs::create_dir(&file).unwrap();
    assert_eq!(shown(&load_from(&file)), defaults());
    assert_eq!(names(&dir), ["settings.json"]);
    assert!(file.is_dir());
    let _ = std::fs::remove_dir_all(&dir);
}

/// Opens `path` the way a process that shares nothing does: nobody else can
/// read or write it until the handle is dropped.
#[cfg(windows)]
fn locked(path: &Path) -> std::fs::File {
    use std::os::windows::fs::OpenOptionsExt;
    std::fs::OpenOptions::new().read(true).share_mode(0).open(path).unwrap()
}

#[cfg(windows)]
#[test]
fn a_file_that_could_not_be_read_is_set_aside_before_it_is_saved_over() {
    let (dir, file) = scratch("locked-then-free");
    std::fs::write(&file, CUSTOM).unwrap();
    let lock = locked(&file);
    let loaded = load_from(&file);
    assert_eq!(shown(&loaded), defaults());
    drop(lock);

    save_to(&file, &loaded).unwrap();
    assert_eq!(kept(&dir, "unread"), [CUSTOM.as_bytes().to_vec()]);
    assert_eq!(shown(&load_from(&file)), defaults());
    let _ = std::fs::remove_dir_all(&dir);
}

#[cfg(windows)]
#[test]
fn a_file_that_still_cannot_be_read_is_not_saved_over() {
    let (dir, file) = scratch("locked");
    std::fs::write(&file, CUSTOM).unwrap();
    let lock = locked(&file);
    let loaded = load_from(&file);
    assert!(save_to(&file, &loaded).is_err());
    drop(lock);
    assert_eq!(std::fs::read(&file).unwrap(), CUSTOM.as_bytes());
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn what_could_not_be_read_is_never_saved_over_unseen() {
    // The same as the two locked-file tests above, on every platform: a
    // directory in the file's place cannot be read either.
    let (dir, file) = scratch("unread");
    std::fs::create_dir(&file).unwrap();
    let loaded = load_from(&file);

    // Still unreadable: the save is refused and nothing is touched.
    assert!(save_to(&file, &loaded).is_err());
    assert!(file.is_dir());
    assert_eq!(names(&dir), ["settings.json"]);

    // Readable by now: kept aside first, and only then saved over.
    std::fs::remove_dir(&file).unwrap();
    std::fs::write(&file, CUSTOM).unwrap();
    save_to(&file, &loaded).unwrap();
    assert_eq!(kept(&dir, "unread"), [CUSTOM.as_bytes().to_vec()]);
    assert_eq!(shown(&load_from(&file)), defaults());
    let _ = std::fs::remove_dir_all(&dir);
}

// ── Copies set aside ──────────────────────────────────────────────────────

#[test]
fn an_unusable_file_is_set_aside_once_however_often_it_is_loaded() {
    let (dir, file) = scratch("once");
    std::fs::write(&file, b"{ broken").unwrap();
    for _ in 0..3 {
        load_from(&file);
    }
    assert_eq!(kept(&dir, "corrupt"), [b"{ broken".to_vec()]);
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn copies_made_within_the_same_second_are_numbered_and_none_is_written_over() {
    let (dir, file) = scratch("numbered");
    let stamp = "20260102-030405";
    let contents = [&b"one"[..], &b"two"[..], &b"three"[..]];
    let copies: Vec<PathBuf> = contents
        .iter()
        .map(|bytes| set_aside_as(&file, "corrupt", stamp, bytes, write_whole).unwrap())
        .collect();

    let named = |suffix: &str| dir.join(format!("settings.json.corrupt-{stamp}{suffix}"));
    assert_eq!(copies, [named(""), named("-1"), named("-2")]);
    for (copy, bytes) in copies.iter().zip(contents) {
        assert_eq!(std::fs::read(copy).unwrap(), bytes);
    }
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn a_copy_that_could_not_be_written_whole_is_removed() {
    fn half_then_fail(file: &mut std::fs::File, bytes: &[u8]) -> std::io::Result<()> {
        use std::io::Write;
        file.write_all(&bytes[..bytes.len() / 2])?;
        Err(std::io::Error::other("the disk is full"))
    }
    let (dir, file) = scratch("half");
    let copy = set_aside_as(&file, "corrupt", "20260102-030405", b"all of it", half_then_fail);
    assert!(copy.is_err());
    assert!(names(&dir).is_empty());
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn a_field_name_cannot_start_a_line_of_its_own_in_the_log() {
    let forged = "volume\r\n2026-01-02 03:04:05 hook id=1 answered allow";
    let line = unusable_field(forged);
    assert!(!line.contains(['\r', '\n']));
    assert!(line.contains(r"volume\r\n2026-01-02"));
}

include!("settings_write_tests.rs");
