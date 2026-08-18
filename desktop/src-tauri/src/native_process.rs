use std::ffi::OsStr;
use std::process::Command;

/// Creates a native child process without allowing Windows to open a console
/// window. All stdout/stderr remain available to the caller for streaming and
/// diagnostics.
pub fn command<S: AsRef<OsStr>>(program: S) -> Command {
    let mut command = Command::new(program);
    configure_hidden(&mut command);
    command
}

pub fn configure_hidden(command: &mut Command) {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        // CREATE_NO_WINDOW: the child inherits no console and no visible CMD
        // flash is produced. This is intentionally applied only on Windows.
        command.creation_flags(0x0800_0000);
    }
}
