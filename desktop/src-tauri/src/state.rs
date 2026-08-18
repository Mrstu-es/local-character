use crate::db::Database;
use crate::engine::EngineRuntime;
use std::sync::{Arc, Mutex};

pub struct AppState {
    pub database: Mutex<Database>,
    pub engine: Arc<Mutex<EngineRuntime>>,
}

impl AppState {
    pub fn initialize() -> Result<Self, String> {
        Ok(Self {
            database: Mutex::new(Database::open()?),
            engine: Arc::new(Mutex::new(EngineRuntime::default())),
        })
    }
}
