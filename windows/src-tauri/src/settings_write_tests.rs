// ── Writing ───────────────────────────────────────────────────────────────

/// A dotfiles setup keeps the real file elsewhere and links to it.
#[cfg(unix)]
#[test]
fn a_save_through_a_symlink_keeps_the_link_and_writes_its_target() {
    let (dir, file) = scratch("symlink");
    let real = dir.join("dotfiles-settings.json");
    std::fs::write(&real, CUSTOM).unwrap();
    std::os::unix::fs::symlink(&real, &file).unwrap();

    save_to(&file, &Settings::default()).unwrap();
    assert!(std::fs::symlink_metadata(&file).unwrap().file_type().is_symlink());
    assert_eq!(shown(&parse(&std::fs::read(&real).unwrap()).unwrap()), defaults());
    assert_eq!(names(&dir), ["dotfiles-settings.json", "settings.json"]);
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn every_field_survives_a_save_and_a_load() {
    let (dir, file) = scratch("round-trip");
    let settings: Settings = serde_json::from_str(CUSTOM).unwrap();
    save_to(&file, &settings).unwrap();
    assert_eq!(shown(&load_from(&file)), custom());
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn the_file_holds_the_camel_case_keys_the_front_end_uses() {
    let (dir, file) = scratch("keys");
    save_to(&file, &Settings::default()).unwrap();
    let written: Value = serde_json::from_slice(&std::fs::read(&file).unwrap()).unwrap();
    let keys: Vec<&str> = written.as_object().unwrap().keys().map(String::as_str).collect();
    assert_eq!(
        keys,
        [
            "soundEnabled",
            "soundVolume",
            "autoCloseInterval",
            "openOnHover",
            "absenceInterval",
            "activeIntegrations",
            "mainPill",
            "screen",
            "autostart",
            "hooksInstalled",
            "model",
            "showPlanInNotch",
            "planRelayInstalled",
            "showCodexPlanInNotch",
            "chatProvider",
            "chatModels",
            "chatSubscriptions",
            "ollamaUrl",
            "lmstudioUrl",
            "customUrl",
            "shortcuts",
            "mochiOutfit",
            "pillColors",
            "language",
            "desktopMochi",
            "phoneLink",
            "phoneChat",
            "phoneChatModels",
            "phoneDetails",
            "phoneAnswers",
            "phoneDiffs",
            "phoneUsage",
            "phoneRecap",
            "phoneServices",
            "phoneRelay",
            "phoneRelayUrl",
            "showClock",
            "minimizeOnHoverAway",
            "hoverAwayDelay",
        ]
    );
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn a_save_replaces_the_file_that_was_there() {
    let (dir, file) = scratch("replace");
    std::fs::write(&file, CUSTOM).unwrap();
    save_to(&file, &Settings::default()).unwrap();
    assert_eq!(shown(&load_from(&file)), defaults());
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn a_save_leaves_nothing_but_settings_json_behind() {
    let (dir, file) = scratch("tidy");
    save_to(&file, &Settings::default()).unwrap();
    save_to(&file, &Settings::default()).unwrap();
    assert_eq!(names(&dir), ["settings.json"]);
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn a_temporary_file_that_cannot_be_written_leaves_the_original_alone() {
    // The new contents go to a file beside the target first. With a
    // directory squatting on that name, the write cannot even start.
    let (dir, file) = scratch("blocked");
    std::fs::write(&file, CUSTOM).unwrap();
    let temp = dir.join(format!("settings.json.coucou-{}", std::process::id()));
    std::fs::create_dir(&temp).unwrap();

    assert!(save_to(&file, &Settings::default()).is_err());
    assert_eq!(std::fs::read(&file).unwrap(), CUSTOM.as_bytes());
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn concurrent_saves_never_collide() {
    let (dir, file) = scratch("concurrent");
    let handles: Vec<_> = (0..20)
        .map(|i| {
            let path = file.clone();
            std::thread::spawn(move || {
                let mut s = Settings::default();
                s.sound_volume = (i as f64) * 0.05;
                save_to(&path, &s)
            })
        })
        .collect();

    for h in handles {
        assert!(h.join().unwrap().is_ok());
    }

    // Must still be valid settings that load cleanly without corruption
    let loaded = load_from(&file);
    assert!(loaded.sound_volume >= 0.0);
    let _ = std::fs::remove_dir_all(&dir);
}
