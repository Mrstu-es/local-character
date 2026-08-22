mod card_parser;
mod commands;
mod db;
mod engine;
mod hardware;
mod model_registry;
mod models;
mod native_process;
mod repository;
mod state;

use state::AppState;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let state = AppState::initialize().expect("No se pudo inicializar Local Character Desktop");
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .manage(state)
        .invoke_handler(tauri::generate_handler![
            commands::get_hardware_snapshot,
            commands::get_diagnostics,
            commands::list_models,
            commands::add_model,
            commands::remove_model,
            commands::list_characters,
            commands::save_character,
            commands::delete_character,
            commands::list_groups,
            commands::save_group,
            commands::delete_group,
            commands::list_providers,
            commands::save_provider,
            commands::delete_provider,
            commands::discover_provider_models,
            commands::read_avatar_data,
            commands::import_character_card,
            commands::import_character_repository,
            commands::import_character_repository_url,
            commands::probe_character_repository_url,
            commands::list_character_repositories,
            commands::list_remote_characters,
            commands::list_voice_repositories,
            commands::list_voice_models,
            commands::sync_voice_repository,
            commands::delete_voice_repository,
            commands::get_explore_filter_catalog,
            commands::sync_character_repository,
            commands::delete_character_repository,
            commands::set_character_repository_enabled,
            commands::install_remote_character,
            commands::load_model,
            commands::unload_model,
            commands::get_engine_status,
            commands::get_engine_logs,
            commands::clear_engine_logs,
            commands::list_conversations,
            commands::save_conversation,
            commands::delete_conversation,
            commands::list_messages,
            commands::get_conversation_summary,
            commands::save_conversation_summary,
            commands::list_semantic_memories,
            commands::save_semantic_memory,
            commands::delete_semantic_memory,
            commands::save_message,
            commands::delete_message,
            commands::find_suspicious_messages,
            commands::delete_suspicious_messages,
            commands::branch_from_message,
            commands::rewind_to_message,
            commands::send_chat_message,
            commands::stop_generation,
            commands::run_benchmark,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Local Character Desktop");
}
