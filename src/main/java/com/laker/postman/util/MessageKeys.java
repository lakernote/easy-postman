package com.laker.postman.util;

/**
 * 国际化消息键常量类
 * 统一管理所有的国际化消息键，避免硬编码字符串
 */
public final class MessageKeys {


    // 私有构造函数，防止实例化
    private MessageKeys() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    // ============ 菜单相关 ============
    public static final String MENU_FILE = "menu.file";
    public static final String MENU_FILE_LOG = "menu.file.log";
    public static final String MENU_FILE_EXIT = "menu.file.exit";
    public static final String MENU_LANGUAGE = "menu.language";
    public static final String MENU_SETTINGS = "menu.settings";
    public static final String MENU_HELP = "menu.help";
    public static final String MENU_HELP_UPDATE = "menu.help.update";
    public static final String MENU_HELP_CHANGELOG = "menu.help.changelog";
    public static final String MENU_HELP_FEEDBACK = "menu.help.feedback";
    public static final String MENU_ABOUT = "menu.about";
    public static final String MENU_ABOUT_EASYPOSTMAN = "menu.about.easypostman";
    public static final String MENU_COLLECTIONS = "menu.collections";
    public static final String MENU_ENVIRONMENTS = "menu.environments";
    public static final String MENU_FUNCTIONAL = "menu.functional";
    public static final String MENU_PERFORMANCE = "menu.performance";
    public static final String MENU_HISTORY = "menu.history";
    public static final String MENU_WORKSPACES = "menu.workspaces";
    public static final String MENU_TOOLBOX = "menu.toolbox";

    // ============ 语言相关 ============
    public static final String LANGUAGE_CHANGED = "language.changed";

    // ============ 更新相关 ============
    public static final String UPDATE_NEW_VERSION_AVAILABLE = "update.new_version_available";
    public static final String UPDATE_LATER = "update.later";
    public static final String UPDATE_VIEW_DETAILS = "update.view_details";
    public static final String UPDATE_MANUAL_DOWNLOAD = "update.manual_download";
    public static final String UPDATE_DOWNLOADING = "update.downloading";
    public static final String UPDATE_CONNECTING = "update.connecting";
    public static final String UPDATE_CANCEL_DOWNLOAD = "update.cancel_download";
    public static final String UPDATE_DOWNLOAD_CANCELLED = "update.download_cancelled";
    public static final String UPDATE_DOWNLOAD_FAILED = "update.download_failed";
    public static final String UPDATE_NO_INSTALLER_FOUND = "update.no_installer_found";

    // ============ 更新类型相关 ============
    public static final String UPDATE_TYPE_INCREMENTAL = "update.type.incremental";
    public static final String UPDATE_TYPE_FULL = "update.type.full";
    public static final String UPDATE_TYPE_SELECT_TITLE = "update.type.select.title";
    public static final String UPDATE_TYPE_SELECT_MESSAGE = "update.type.select.message";
    public static final String UPDATE_INCREMENTAL_WARNING = "update.incremental.warning";
    public static final String UPDATE_FULL_RECOMMENDED = "update.full.recommended";
    public static final String UPDATE_JAR_INSTALL_PROMPT = "update.jar.install.prompt";
    public static final String UPDATE_INSTALLER_INSTALL_PROMPT = "update.installer.install.prompt";

    // ============ 版本检查器相关 ============
    public static final String UPDATE_FETCH_RELEASE_FAILED = "update.fetch_release_failed";
    public static final String UPDATE_NO_VERSION_INFO = "update.no_version_info";
    public static final String UPDATE_ALREADY_LATEST = "update.already_latest";
    // ============ 更新日志相关 ============
    public static final String CHANGELOG_TITLE = "changelog.title";
    public static final String CHANGELOG_CURRENT_VERSION = "changelog.current_version";
    public static final String CHANGELOG_LOADING = "changelog.loading";
    public static final String CHANGELOG_LOAD_FAILED = "changelog.load_failed";
    public static final String CHANGELOG_NO_RELEASES = "changelog.no_releases";
    public static final String CHANGELOG_VIEW_ON_GITHUB = "changelog.view_on_github";
    public static final String CHANGELOG_VIEW_ON_GITEE = "changelog.view_on_gitee";
    public static final String CHANGELOG_CLOSE = "changelog.close";
    public static final String CHANGELOG_REFRESH = "changelog.refresh";


    // ============ 错误消息 ============
    public static final String ERROR_UPDATE_FAILED = "error.update_failed";
    public static final String ERROR_OPEN_LOG_MESSAGE = "error.open_log_message";
    public static final String ERROR_OPEN_LINK_FAILED = "error.open_link_failed";
    public static final String ERROR_NETWORK_TIMEOUT = "error.network_timeout";
    public static final String ERROR_SERVER_UNREACHABLE = "error.server_unreachable";
    public static final String ERROR_INVALID_DOWNLOAD_LINK = "error.invalid_download_link";
    public static final String ERROR_DISK_SPACE_INSUFFICIENT = "error.disk_space_insufficient";
    public static final String ERROR_PERMISSION_DENIED = "error.permission_denied";
    public static final String ERROR_IO_EXCEPTION = "error.io_exception";

    // ============ 关于对话框 ============
    public static final String ABOUT_VERSION = "about.version";
    public static final String ABOUT_AUTHOR = "about.author";
    public static final String ABOUT_LICENSE = "about.license";
    public static final String ABOUT_WECHAT = "about.wechat";
    public static final String ABOUT_BLOG = "about.blog";
    public static final String ABOUT_GITHUB = "about.github";
    public static final String ABOUT_GITEE = "about.gitee";

    // ============ 反馈 ============
    public static final String FEEDBACK_MESSAGE = "feedback.message";
    public static final String FEEDBACK_TITLE = "feedback.title";

    // ============ 通用 ============
    public static final String GENERAL_ERROR = "general.error";
    public static final String GENERAL_ERROR_MESSAGE = "general.error.message";
    public static final String GENERAL_INFO = "general.info";
    public static final String GENERAL_TIP = "general.tip";
    public static final String GENERAL_OK = "general.ok";
    public static final String GENERAL_CANCEL = "button.cancel";
    public static final String GENERAL_SEARCH = "general.search";
    public static final String CONSOLE_TITLE = "console.title";

    // ============ 工作区选择对话框 ============
    public static final String WORKSPACE_SELECT_HINT = "workspace.select.hint";
    public static final String WORKSPACE_SELECT_REQUIRED = "workspace.select.required";
    public static final String WORKSPACE_NO_OTHER_AVAILABLE = "workspace.no_other_available";

    // ============ 工作区转移统一消息键 ============
    public static final String WORKSPACE_TRANSFER_MENU_ITEM = "workspace.transfer.menu_item";
    public static final String WORKSPACE_TRANSFER_SELECT_DIALOG_TITLE = "workspace.transfer.select_dialog_title";
    public static final String WORKSPACE_TRANSFER_CONFIRM_MESSAGE = "workspace.transfer.confirm_message";
    public static final String WORKSPACE_TRANSFER_CONFIRM_TITLE = "workspace.transfer.confirm_title";
    public static final String WORKSPACE_TRANSFER_SUCCESS = "workspace.transfer.success";
    public static final String WORKSPACE_TRANSFER_FAIL = "workspace.transfer.fail";

    // ============ 按钮 ============
    public static final String BUTTON_SEND = "button.send";
    public static final String BUTTON_SAVE = "button.save";
    public static final String BUTTON_SAVE_TOOLTIP = "button.save.tooltip";
    public static final String BUTTON_CANCEL = "button.cancel";
    public static final String BUTTON_CLOSE = "button.close";
    public static final String BUTTON_START = "button.start";
    public static final String BUTTON_STOP = "button.stop";
    public static final String BUTTON_SEARCH = "button.search";
    public static final String BUTTON_LOAD = "button.load";
    public static final String BUTTON_CLEAR = "button.clear";
    public static final String BUTTON_REFRESH = "button.refresh";
    public static final String BUTTON_CONNECT = "button.connect";
    public static final String BUTTON_CALCULATE = "button.calculate";

    // ============ 请求相关 ============
    public static final String NEW_REQUEST = "new.request";
    public static final String CREATE_NEW_REQUEST = "create.new.request";
    public static final String SAVE_REQUEST = "save.request";
    public static final String REQUEST_NAME = "request.name";
    public static final String REQUEST_URL_PLACEHOLDER = "request.url.placeholder";
    public static final String SELECT_GROUP = "select.group";
    public static final String PLEASE_ENTER_REQUEST_NAME = "please.enter.request.name";
    public static final String PLEASE_SELECT_GROUP = "please.select.group";
    public static final String PLEASE_SELECT_VALID_GROUP = "please.select.valid.group";
    public static final String SUCCESS = "success";
    public static final String UPDATE_REQUEST_FAILED = "update.request.failed";
    public static final String ERROR = "error";

    // ============ 剪贴板和cURL ============
    public static final String CLIPBOARD_CURL_DETECTED = "clipboard.curl.detected";
    public static final String IMPORT_CURL = "import.curl";
    public static final String PARSE_CURL_ERROR = "parse.curl.error";
    public static final String TIP = "tip";

    // ============ 标签页 ============
    public static final String TAB_PARAMS = "tab.params";
    public static final String TAB_AUTHORIZATION = "tab.authorization";
    public static final String TAB_SCRIPTS = "tab.scripts";
    public static final String TAB_TESTS = "tab.tests";
    public static final String TAB_NETWORK_LOG = "tab.network_log";
    public static final String TAB_REQUEST_HEADERS = "tab.request_headers";
    public static final String TAB_REQUEST_BODY = "tab.request_body";
    public static final String TAB_RESPONSE_HEADERS = "tab.response_headers";
    public static final String TAB_RESPONSE_BODY = "tab.response_body";
    public static final String TAB_CLOSE_OTHERS = "tab.close_others";
    public static final String TAB_CLOSE_ALL = "tab.close_all";
    public static final String TAB_UNSAVED_CHANGES_SAVE_CURRENT = "tab.unsaved_changes.save_current";
    public static final String TAB_UNSAVED_CHANGES_SAVE_OTHERS = "tab.unsaved_changes.save_others";
    public static final String TAB_UNSAVED_CHANGES_SAVE_ALL = "tab.unsaved_changes.save_all";
    public static final String TAB_UNSAVED_CHANGES_TITLE = "tab.unsaved_changes.title";
    public static final String TAB_CLOSE_CURRENT = "tab.close_current";

    // ============ 快捷键设置 ============
    public static final String SHORTCUT_SETTINGS_TITLE = "shortcut.settings.title";
    public static final String SHORTCUT_ACTION = "shortcut.action";
    public static final String SHORTCUT_KEY = "shortcut.key";
    public static final String SHORTCUT_EDIT = "shortcut.edit";
    public static final String SHORTCUT_RESET = "shortcut.reset";
    public static final String SHORTCUT_SELECT_FIRST = "shortcut.select_first";
    public static final String SHORTCUT_EDIT_TITLE = "shortcut.edit_title";
    public static final String SHORTCUT_CURRENT = "shortcut.current";
    public static final String SHORTCUT_NEW = "shortcut.new";
    public static final String SHORTCUT_PRESS_KEY = "shortcut.press_key";
    public static final String SHORTCUT_CONFLICT = "shortcut.conflict";
    public static final String SHORTCUT_NOT_SET = "shortcut.not_set";
    public static final String SHORTCUT_RESET_CONFIRM = "shortcut.reset_confirm";
    public static final String SHORTCUT_RESET_SUCCESS = "shortcut.reset_success";
    public static final String EXIT_APP = "exit.app";
    public static final String SETTINGS_SHORTCUTS_TITLE = "settings.shortcuts.title";
    public static final String SHORTCUT_LABEL_FORMAT = "shortcut.label.format";
    public static final String SHORTCUT_DOUBLE_CLICK_HINT = "shortcut.double_click_hint";

    // ============ 状态相关 ============
    public static final String STATUS_CANCELED = "status.canceled";
    public static final String STATUS_STATUS = "status.status";
    public static final String STATUS_DURATION = "status.duration";
    public static final String STATUS_RESPONSE_SIZE = "status.response_size";

    // ============ WebSocket相关 ============
    public static final String WEBSOCKET_FAILED = "websocket.failed";
    public static final String WEBSOCKET_ERROR = "websocket.error";
    public static final String WEBSOCKET_NOT_CONNECTED = "websocket.not_connected";

    // ============ SSE相关 ============
    public static final String SSE_FAILED = "sse.failed";
    public static final String SSE_ERROR = "sse.error";

    // ============ 脚本相关 ============
    public static final String SCRIPT_TAB_PRESCRIPT = "script.tab.prescript";
    public static final String SCRIPT_TAB_POSTSCRIPT = "script.tab.postscript";
    public static final String SCRIPT_TAB_HELP = "script.tab.help";
    public static final String SCRIPT_BUTTON_SNIPPETS = "script.button.snippets";
    public static final String SCRIPT_HELP_TEXT = "script.help.text";

    // ============ 认证相关 ============
    public static final String AUTH_TYPE_LABEL = "auth.type.label";
    public static final String AUTH_TYPE_INHERIT = "auth.type.inherit";
    public static final String AUTH_TYPE_NONE = "auth.type.none";
    public static final String AUTH_TYPE_BASIC = "auth.type.basic";
    public static final String AUTH_TYPE_BEARER = "auth.type.bearer";
    public static final String AUTH_TYPE_INHERIT_DESC = "auth.type.inherit.desc";
    public static final String AUTH_TYPE_NONE_DESC = "auth.type.none.desc";
    public static final String AUTH_TYPE_BASIC_DESC = "auth.type.basic.desc";
    public static final String AUTH_TYPE_BEARER_DESC = "auth.type.bearer.desc";
    public static final String AUTH_USERNAME = "auth.username";
    public static final String AUTH_PASSWORD = "auth.password";
    public static final String AUTH_TOKEN = "auth.token";

    // ============ Cookie相关 ============
    public static final String COOKIES_TITLE = "cookies.title";
    public static final String COOKIES_MANAGER_TITLE = "cookies.manager_title";
    public static final String COOKIE_BUTTON_DELETE = "cookie.button.delete";
    public static final String COOKIE_BUTTON_CLEAR = "cookie.button.clear";
    public static final String COOKIE_BUTTON_ADD = "cookie.button.add";
    public static final String COOKIE_BUTTON_EDIT = "cookie.button.edit";
    public static final String COOKIE_BUTTON_REFRESH = "cookie.button.refresh";
    public static final String COOKIE_TOOLTIP_ADD = "cookie.tooltip.add";
    public static final String COOKIE_TOOLTIP_EDIT = "cookie.tooltip.edit";
    public static final String COOKIE_TOOLTIP_DELETE = "cookie.tooltip.delete";
    public static final String COOKIE_TOOLTIP_CLEAR = "cookie.tooltip.clear";
    public static final String COOKIE_TOOLTIP_REFRESH = "cookie.tooltip.refresh";
    public static final String COOKIE_DIALOG_CLEAR_CONFIRM = "cookie.dialog.clear_confirm";
    public static final String COOKIE_DIALOG_CLEAR_CONFIRM_TITLE = "cookie.dialog.clear_confirm_title";
    public static final String COOKIE_DIALOG_ADD_TITLE = "cookie.dialog.add_title";
    public static final String COOKIE_DIALOG_EDIT_TITLE = "cookie.dialog.edit_title";
    public static final String COOKIE_DIALOG_ERROR_EMPTY = "cookie.dialog.error.empty";
    public static final String COOKIE_DIALOG_ERROR_TITLE = "cookie.dialog.error.title";
    public static final String COOKIE_DELETE_CONFIRM = "cookie.delete_confirm";
    public static final String COOKIE_DELETE_CONFIRM_TITLE = "cookie.delete_confirm_title";
    public static final String COOKIE_ERROR_NO_SELECTION = "cookie.error_no_selection";
    public static final String COOKIE_SEARCH_PLACEHOLDER = "cookie.search_placeholder";
    public static final String COOKIE_EMPTY_STATE = "cookie.empty_state";
    public static final String COOKIE_EMPTY_STATE_HINT = "cookie.empty_state_hint";
    public static final String COOKIE_EDIT_HINT = "cookie.edit_hint";
    public static final String COOKIE_COLUMN_NAME = "cookie.column.name";
    public static final String COOKIE_COLUMN_VALUE = "cookie.column.value";
    public static final String COOKIE_COLUMN_DOMAIN = "cookie.column.domain";
    public static final String COOKIE_COLUMN_PATH = "cookie.column.path";
    public static final String COOKIE_COLUMN_EXPIRES = "cookie.column.expires";
    public static final String COOKIE_COLUMN_SECURE = "cookie.column.secure";
    public static final String COOKIE_COLUMN_HTTPONLY = "cookie.column.httponly";
    public static final String COOKIE_FIELD_NAME = "cookie.field.name";
    public static final String COOKIE_FIELD_VALUE = "cookie.field.value";
    public static final String COOKIE_FIELD_DOMAIN = "cookie.field.domain";
    public static final String COOKIE_FIELD_PATH = "cookie.field.path";
    public static final String COOKIE_FIELD_SECURE = "cookie.field.secure";
    public static final String COOKIE_FIELD_HTTPONLY = "cookie.field.httponly";

    // ============ 环境变量相关 ============
    public static final String ENV_BUTTON_ADD = "env.button.add";
    public static final String ENV_BUTTON_RENAME = "env.button.rename";
    public static final String ENV_BUTTON_DUPLICATE = "env.button.duplicate";
    public static final String ENV_BUTTON_DELETE = "env.button.delete";
    public static final String ENV_BUTTON_EXPORT_POSTMAN = "env.button.export_postman";
    public static final String ENV_MENU_IMPORT_EASY = "env.menu.import_easy";
    public static final String ENV_MENU_IMPORT_POSTMAN = "env.menu.import_postman";
    public static final String ENV_DIALOG_SAVE_CHANGES = "env.dialog.save_changes";
    public static final String ENV_DIALOG_SAVE_CHANGES_TITLE = "env.dialog.save_changes.title";
    public static final String ENV_DIALOG_EXPORT_TITLE = "env.dialog.export.title";
    public static final String ENV_DIALOG_EXPORT_SUCCESS = "env.dialog.export.success";
    public static final String ENV_DIALOG_EXPORT_FAIL = "env.dialog.export.fail";
    public static final String ENV_DIALOG_IMPORT_EASY_TITLE = "env.dialog.import_easy.title";
    public static final String ENV_DIALOG_IMPORT_EASY_SUCCESS = "env.dialog.import_easy.success";
    public static final String ENV_DIALOG_IMPORT_EASY_FAIL = "env.dialog.import_easy.fail";
    public static final String ENV_DIALOG_IMPORT_POSTMAN_TITLE = "env.dialog.import_postman.title";
    public static final String ENV_DIALOG_IMPORT_POSTMAN_FAIL = "env.dialog.import_postman.fail";
    public static final String ENV_DIALOG_IMPORT_POSTMAN_INVALID = "env.dialog.import_postman.invalid";
    public static final String ENV_DIALOG_ADD_TITLE = "env.dialog.add.title";
    public static final String ENV_DIALOG_ADD_PROMPT = "env.dialog.add.prompt";
    public static final String ENV_DIALOG_RENAME_TITLE = "env.dialog.rename.title";
    public static final String ENV_DIALOG_RENAME_PROMPT = "env.dialog.rename.prompt";
    public static final String ENV_DIALOG_RENAME_FAIL = "env.dialog.rename.fail";
    public static final String ENV_DIALOG_DELETE_TITLE = "env.dialog.delete.title";
    public static final String ENV_DIALOG_DELETE_PROMPT = "env.dialog.delete.prompt";
    public static final String ENV_DIALOG_COPY_FAIL = "env.dialog.copy.fail";
    public static final String ENV_NAME_COPY_SUFFIX = "env.name.copy_suffix";
    public static final String ENV_DIALOG_EXPORT_POSTMAN_TITLE = "env.dialog.export_postman.title";
    public static final String ENV_DIALOG_EXPORT_POSTMAN_SUCCESS = "env.dialog.export_postman.success";
    public static final String ENV_DIALOG_EXPORT_POSTMAN_FAIL = "env.dialog.export_postman.fail";

    // ============ 功能测试相关 ============
    public static final String FUNCTIONAL_TAB_REQUEST_CONFIG = "functional.tab.request_config";
    public static final String FUNCTIONAL_TAB_EXECUTION_RESULTS = "functional.tab.execution_results";
    public static final String FUNCTIONAL_MSG_NO_RUNNABLE_REQUEST = "functional.msg.no_runnable_request";
    public static final String FUNCTIONAL_MSG_CSV_DETECTED = "functional.msg.csv_detected";
    public static final String FUNCTIONAL_MSG_CSV_TITLE = "functional.msg.csv_title";
    public static final String FUNCTIONAL_STATUS_NOT_EXECUTED = "functional.status.not_executed";
    public static final String FUNCTIONAL_STATUS_PRE_SCRIPT_FAILED = "functional.status.pre_script_failed";
    public static final String FUNCTIONAL_STATUS_SSE_BATCH_NOT_SUPPORTED = "functional.status.sse_batch_not_supported";
    public static final String FUNCTIONAL_STATUS_WS_BATCH_NOT_SUPPORTED = "functional.status.ws_batch_not_supported";

    // ============ 工作区相关 ============
    public static final String WORKSPACE_NEW = "workspace.new";
    public static final String WORKSPACE_CREATE = "workspace.create";
    public static final String WORKSPACE_NAME = "workspace.name";
    public static final String WORKSPACE_DEFAULT_NAME = "workspace.default.name";
    public static final String WORKSPACE_DEFAULT_DESCRIPTION = "workspace.default.description";
    public static final String WORKSPACE_DESCRIPTION = "workspace.description";
    public static final String WORKSPACE_TYPE = "workspace.type";
    public static final String WORKSPACE_TYPE_LOCAL = "workspace.type.local";
    public static final String WORKSPACE_TYPE_GIT = "workspace.type.git";
    public static final String WORKSPACE_PATH = "workspace.path";
    public static final String WORKSPACE_SELECT_PATH = "workspace.select.path";
    public static final String WORKSPACE_GIT_URL = "workspace.git.url";
    public static final String WORKSPACE_GIT_USERNAME = "workspace.git.username";
    public static final String WORKSPACE_GIT_PASSWORD = "workspace.git.password";
    public static final String WORKSPACE_GIT_TOKEN = "workspace.git.token";
    public static final String WORKSPACE_GIT_AUTH_TYPE = "workspace.git.auth.type";
    public static final String WORKSPACE_GIT_AUTH_NONE = "workspace.git.auth.none";
    public static final String WORKSPACE_GIT_AUTH_PASSWORD = "workspace.git.auth.password";
    public static final String WORKSPACE_GIT_AUTH_TOKEN = "workspace.git.auth.token";
    public static final String WORKSPACE_GIT_AUTH_SSH = "workspace.git.auth.ssh";
    // 添加SSH认证相关的消息键
    public static final String WORKSPACE_GIT_SSH_SELECT_KEY = "workspace.git.ssh.select_key";
    public static final String WORKSPACE_GIT_SSH_PASSPHRASE = "workspace.git.ssh.passphrase";
    public static final String WORKSPACE_GIT_SSH_KEY_PATH = "workspace.git.ssh.private_key";
    public static final String WORKSPACE_CLONE_FROM_REMOTE = "workspace.clone.from.remote";
    public static final String WORKSPACE_INIT_LOCAL = "workspace.init.local";
    public static final String WORKSPACE_RENAME = "workspace.rename";
    public static final String WORKSPACE_DELETE = "workspace.delete";
    public static final String WORKSPACE_DELETE_CONFIRM = "workspace.delete.confirm";
    public static final String WORKSPACE_SWITCH = "workspace.switch";
    public static final String WORKSPACE_INFO = "workspace.info";
    public static final String WORKSPACE_GIT_PULL = "workspace.git.pull";
    public static final String WORKSPACE_GIT_PUSH = "workspace.git.push";
    public static final String WORKSPACE_GIT_COMMIT = "workspace.git.commit";
    public static final String WORKSPACE_GIT_AUTH_UPDATE = "workspace.git.auth.update";
    public static final String WORKSPACE_GIT_AUTH_UPDATE_SUCCESS = "workspace.git.auth.update.success";
    public static final String WORKSPACE_GIT_AUTH_UPDATE_FAILED = "workspace.git.auth.update.failed";
    public static final String WORKSPACE_VALIDATION_NAME_REQUIRED = "workspace.validation.name.required";
    public static final String WORKSPACE_VALIDATION_PATH_REQUIRED = "workspace.validation.path.required";
    public static final String WORKSPACE_VALIDATION_GIT_URL_REQUIRED = "workspace.validation.git.url.required";
    public static final String WORKSPACE_VALIDATION_GIT_BRANCH_INVALID = "workspace.validation.git.branch.invalid";
    public static final String WORKSPACE_VALIDATION_AUTH_REQUIRED = "workspace.validation.auth.required";
    public static final String WORKSPACE_AUTO_GENERATE_PATH = "workspace.auto_generate_path";

    // WorkspaceCreateDialog specific keys
    public static final String WORKSPACE_CREATE_DIALOG_BRANCH_LABEL = "workspace.create.dialog.branch.label";
    public static final String WORKSPACE_CREATE_DIALOG_CREATING = "workspace.create.dialog.creating";
    public static final String WORKSPACE_CREATE_DIALOG_CREATION_COMPLETED = "workspace.create.dialog.creation.completed";
    public static final String WORKSPACE_CREATE_DIALOG_CREATION_FAILED = "workspace.create.dialog.creation.failed";
    public static final String WORKSPACE_CREATE_DIALOG_CREATION_FAILED_WITH_MESSAGE = "workspace.create.dialog.creation.failed.with.message";

    // ProgressDialog keys
    public static final String PROGRESS_DIALOG_OPERATION_SUCCESS = "progress.dialog.operation.success";
    public static final String PROGRESS_DIALOG_OPERATION_COMPLETED_CLOSING = "progress.dialog.operation.completed.closing";
    public static final String PROGRESS_DIALOG_OPERATION_FAILED = "progress.dialog.operation.failed";
    public static final String PROGRESS_DIALOG_CHECK_INPUT_RETRY = "progress.dialog.check.input.retry";
    public static final String PROGRESS_DIALOG_OPERATION_FAILED_WITH_MESSAGE = "progress.dialog.operation.failed.with.message";
    public static final String PROGRESS_DIALOG_CONFIRM_CANCEL_OPERATION = "progress.dialog.confirm.cancel.operation";
    public static final String PROGRESS_DIALOG_CONFIRM_CANCEL_TITLE = "progress.dialog.confirm.cancel.title";

    // ProgressPanel keys
    public static final String PROGRESS_PANEL_READY = "progress.panel.ready";
    public static final String PROGRESS_PANEL_FILL_CONFIG = "progress.panel.fill_config";

    // ============ 工作区详情面板相关 ============
    public static final String WORKSPACE_DETAIL_BASIC_INFO = "workspace.detail.basic_info";
    public static final String WORKSPACE_DETAIL_CREATED_TIME = "workspace.detail.created_time";
    public static final String WORKSPACE_DETAIL_GIT_INFO = "workspace.detail.git_info";
    public static final String WORKSPACE_DETAIL_REPO_SOURCE = "workspace.detail.repo_source";
    public static final String WORKSPACE_DETAIL_REMOTE_REPO = "workspace.detail.remote_repo";
    public static final String WORKSPACE_DETAIL_LOCAL_BRANCH = "workspace.detail.local_branch";
    public static final String WORKSPACE_DETAIL_REMOTE_BRANCH = "workspace.detail.remote_branch";
    public static final String WORKSPACE_DETAIL_LAST_COMMIT = "workspace.detail.last_commit";

    // ============ 性能测试相关 ============
    public static final String PERFORMANCE_TAB_TREND = "performance.tab.trend";
    public static final String PERFORMANCE_TAB_REPORT = "performance.tab.report";
    public static final String PERFORMANCE_TAB_RESULT_TREE = "performance.tab.result_tree";
    public static final String PERFORMANCE_TAB_REQUEST = "performance.tab.request";
    public static final String PERFORMANCE_TAB_RESPONSE = "performance.tab.response";
    public static final String PERFORMANCE_TAB_TESTS = "performance.tab.tests";
    public static final String PERFORMANCE_TAB_TIMING = "performance.tab.timing";
    public static final String PERFORMANCE_TAB_EVENT_INFO = "performance.tab.event_info";
    public static final String PERFORMANCE_NO_ASSERTION_RESULTS = "performance.no_assertion_results";
    public static final String PERFORMANCE_NO_TIMING_INFO = "performance.no_timing_info";
    public static final String PERFORMANCE_NO_EVENT_INFO = "performance.no_event_info";
    public static final String PERFORMANCE_PROPERTY_SELECT_NODE = "performance.property.select_node";
    public static final String PERFORMANCE_EFFICIENT_MODE = "performance.efficient_mode";
    public static final String PERFORMANCE_EFFICIENT_MODE_TOOLTIP = "performance.efficient_mode.tooltip";
    public static final String PERFORMANCE_EFFICIENT_MODE_HELP = "performance.efficient_mode.help";
    public static final String PERFORMANCE_EFFICIENT_MODE_DESC = "performance.efficient_mode.desc";
    public static final String PERFORMANCE_EFFICIENT_MODE_HELP_TITLE = "performance.efficient_mode.help_title";
    public static final String PERFORMANCE_PROGRESS_TOOLTIP = "performance.progress.tooltip";
    public static final String PERFORMANCE_MENU_ADD_THREAD_GROUP = "performance.menu.add_thread_group";
    public static final String PERFORMANCE_MENU_ADD_REQUEST = "performance.menu.add_request";
    public static final String PERFORMANCE_MENU_ADD_ASSERTION = "performance.menu.add_assertion";
    public static final String PERFORMANCE_MENU_ADD_TIMER = "performance.menu.add_timer";
    public static final String PERFORMANCE_MENU_RENAME = "performance.menu.rename";
    public static final String PERFORMANCE_MENU_DELETE = "performance.menu.delete";
    public static final String PERFORMANCE_MSG_SELECT_THREAD_GROUP = "performance.msg.select_thread_group";
    public static final String PERFORMANCE_MSG_RENAME_NODE = "performance.msg.rename_node";
    public static final String PERFORMANCE_MSG_EXECUTION_INTERRUPTED = "performance.msg.execution_interrupted";
    public static final String PERFORMANCE_MSG_EXECUTION_COMPLETED = "performance.msg.execution_completed";
    public static final String PERFORMANCE_MSG_PRE_SCRIPT_FAILED = "performance.msg.pre_script_failed";
    public static final String PERFORMANCE_MSG_REQUEST_FAILED = "performance.msg.request_failed";
    public static final String PERFORMANCE_MSG_ASSERTION_FAILED = "performance.msg.assertion_failed";
    public static final String PERFORMANCE_TEST_PLAN = "performance.test_plan";
    public static final String PERFORMANCE_THREAD_GROUP = "performance.thread_group";
    public static final String PERFORMANCE_DEFAULT_REQUEST = "performance.default_request";

    // ============ 性能趋势相关 ============
    public static final String PERFORMANCE_TREND_THREADS = "performance.trend.threads";
    public static final String PERFORMANCE_TREND_RESPONSE_TIME_MS = "performance.trend.response_time_ms";
    public static final String PERFORMANCE_TREND_RESPONSE_TIME = "performance.trend.response_time";
    public static final String PERFORMANCE_TREND_QPS = "performance.trend.qps";
    public static final String PERFORMANCE_TREND_ERROR_RATE_PERCENT = "performance.trend.error_rate_percent";
    public static final String PERFORMANCE_TREND_ERROR_RATE = "performance.trend.error_rate";
    public static final String PERFORMANCE_TREND_CHART_TITLE = "performance.trend.chart_title";
    public static final String PERFORMANCE_TREND_TIME = "performance.trend.time";
    public static final String PERFORMANCE_TREND_METRIC_VALUE = "performance.trend.metric_value";
    public static final String PERFORMANCE_TREND_METRICS = "performance.trend.metrics";
    public static final String PERFORMANCE_TREND_NO_METRIC_SELECTED = "performance.trend.no_metric_selected";

    // ============ 历史记录相关 ============
    public static final String HISTORY_EMPTY_BODY = "history.empty_body";
    public static final String HISTORY_TODAY = "history.today";
    public static final String HISTORY_YESTERDAY = "history.yesterday";

    // ============ Tab标签页相关 ============
    public static final String TAB_REQUEST = "tab.request";
    public static final String TAB_RESPONSE = "tab.response";
    public static final String TAB_TIMING = "tab.timing";
    public static final String TAB_EVENTS = "tab.events";

    // ============ 应用相关 ============
    public static final String APP_NAME = "app.name";
    public static final String SPLASH_STATUS_STARTING = "splash.status.starting";
    public static final String SPLASH_STATUS_LOADING_MAIN = "splash.status.loading_main";
    public static final String SPLASH_STATUS_INITIALIZING = "splash.status.initializing";
    public static final String SPLASH_STATUS_READY = "splash.status.ready";
    public static final String SPLASH_STATUS_DONE = "splash.status.done";
    public static final String SPLASH_ERROR_LOAD_MAIN = "splash.error.load_main";

    // ============ 退出相关 ============
    public static final String EXIT_UNSAVED_CHANGES = "exit.unsaved_changes";
    public static final String EXIT_UNSAVED_CHANGES_TITLE = "exit.unsaved_changes.title";
    public static final String EXIT_SAVE_ALL = "exit.save_all";
    public static final String EXIT_DISCARD_ALL = "exit.discard_all";
    public static final String EXIT_CANCEL = "exit.cancel";

    // ============ 集合相关 ============
    public static final String COLLECTIONS_EXPORT_TOOLTIP = "collections.export.tooltip";
    public static final String COLLECTIONS_IMPORT_TOOLTIP = "collections.import.tooltip";
    public static final String COLLECTIONS_IMPORT_CURL_DETECTED = "collections.import.curl.detected";
    public static final String COLLECTIONS_IMPORT_CURL_TITLE = "collections.import.curl.title";
    public static final String COLLECTIONS_IMPORT_EASY = "collections.import.easy";
    public static final String COLLECTIONS_IMPORT_POSTMAN = "collections.import.postman";
    public static final String COLLECTIONS_IMPORT_HAR = "collections.import.har";
    public static final String COLLECTIONS_IMPORT_CURL = "collections.import.curl";

    // ============ 集合菜单相关 ============
    public static final String COLLECTIONS_MENU_ADD_GROUP = "collections.menu.add_group";
    public static final String COLLECTIONS_MENU_ADD_ROOT_GROUP = "collections.menu.add_root_group";
    public static final String COLLECTIONS_MENU_ADD_REQUEST = "collections.menu.add_request";
    public static final String COLLECTIONS_MENU_DUPLICATE = "collections.menu.duplicate";
    public static final String COLLECTIONS_MENU_EXPORT_POSTMAN = "collections.menu.export_postman";
    public static final String COLLECTIONS_MENU_COPY_CURL = "collections.menu.copy_curl";
    public static final String COLLECTIONS_MENU_RENAME = "collections.menu.rename";
    public static final String COLLECTIONS_MENU_DELETE = "collections.menu.delete";
    public static final String COLLECTIONS_MENU_COPY_SUFFIX = "collections.menu.copy_suffix";
    public static final String COLLECTIONS_MENU_COPY_CURL_SUCCESS = "collections.menu.copy_curl.success";
    public static final String COLLECTIONS_MENU_COPY_CURL_FAIL = "collections.menu.copy_curl.fail";
    public static final String COLLECTIONS_MENU_COPY = "collections.menu.copy";
    public static final String COLLECTIONS_MENU_PASTE = "collections.menu.paste";
    public static final String COLLECTIONS_COPY_SUCCESS = "collections.copy_success";
    public static final String COLLECTIONS_COPIED_TO_CLIPBOARD = "collections.copied_to_clipboard";
    public static final String COLLECTIONS_PASTE_SUCCESS = "collections.paste_success";
    public static final String COLLECTIONS_MENU_EXPORT_POSTMAN_SELECT_GROUP = "collections.menu.export_postman.select_group";
    public static final String COLLECTIONS_MENU_EXPORT_POSTMAN_DIALOG_TITLE = "collections.menu.export_postman.dialog_title";

    // ============ 集合导出导入相关 ============
    public static final String COLLECTIONS_EXPORT_DIALOG_TITLE = "collections.export.dialog_title";
    public static final String COLLECTIONS_EXPORT_SUCCESS = "collections.export.success";
    public static final String COLLECTIONS_EXPORT_FAIL = "collections.export.fail";
    public static final String COLLECTIONS_IMPORT_DIALOG_TITLE = "collections.import.dialog_title";
    public static final String COLLECTIONS_IMPORT_SUCCESS = "collections.import.success";
    public static final String COLLECTIONS_IMPORT_FAIL = "collections.import.fail";
    public static final String COLLECTIONS_IMPORT_POSTMAN_DIALOG_TITLE = "collections.import.postman.dialog_title";
    public static final String COLLECTIONS_IMPORT_POSTMAN_INVALID = "collections.import.postman.invalid";
    public static final String COLLECTIONS_IMPORT_HAR_DIALOG_TITLE = "collections.import.har.dialog_title";
    public static final String COLLECTIONS_IMPORT_HAR_INVALID = "collections.import.har.invalid";
    public static final String COLLECTIONS_IMPORT_CURL_DIALOG_TITLE = "collections.import.curl.dialog_title";
    public static final String COLLECTIONS_IMPORT_CURL_DIALOG_PROMPT = "collections.import.curl.dialog_prompt";
    public static final String COLLECTIONS_IMPORT_CURL_PARSE_FAIL = "collections.import.curl.parse_fail";
    public static final String COLLECTIONS_IMPORT_CURL_PARSE_ERROR = "collections.import.curl.parse_error";

    // ============ 集合对话框相关 ============
    public static final String COLLECTIONS_DIALOG_ADD_GROUP_PROMPT = "collections.dialog.add_group.prompt";
    public static final String COLLECTIONS_DIALOG_RENAME_GROUP_PROMPT = "collections.dialog.rename_group.prompt";
    public static final String COLLECTIONS_DIALOG_RENAME_GROUP_TITLE = "collections.dialog.rename_group.title";
    public static final String COLLECTIONS_DIALOG_ADD_REQUEST_TITLE = "collections.dialog.add_request.title";
    public static final String COLLECTIONS_DIALOG_ADD_REQUEST_NAME = "collections.dialog.add_request.name";
    public static final String COLLECTIONS_DIALOG_ADD_REQUEST_PROTOCOL = "collections.dialog.add_request.protocol";
    public static final String COLLECTIONS_DIALOG_ADD_REQUEST_NAME_EMPTY = "collections.dialog.add_request.name_empty";
    public static final String COLLECTIONS_DIALOG_RENAME_GROUP_EMPTY = "collections.dialog.rename_group.empty";
    public static final String COLLECTIONS_DIALOG_RENAME_REQUEST_PROMPT = "collections.dialog.rename_request.prompt";
    public static final String COLLECTIONS_DIALOG_RENAME_REQUEST_TITLE = "collections.dialog.rename_request.title";
    public static final String COLLECTIONS_DIALOG_RENAME_REQUEST_EMPTY = "collections.dialog.rename_request.empty";
    public static final String COLLECTIONS_DIALOG_MULTI_SELECT_TITLE = "collections.dialog.multi_select.title";
    public static final String COLLECTIONS_DIALOG_MULTI_SELECT_EMPTY = "collections.dialog.multi_select.empty";
    public static final String COLLECTIONS_DELETE_CONFIRM = "collections.delete.confirm";
    public static final String COLLECTIONS_DELETE_BATCH_CONFIRM = "collections.delete.batch_confirm";
    public static final String COLLECTIONS_DELETE_CONFIRM_TITLE = "collections.delete.confirm_title";

    // ============ Group Edit Panel 相关 ============
    public static final String GROUP_EDIT_TITLE = "group.edit.title";
    public static final String GROUP_EDIT_TAB_GENERAL = "group.edit.tab.general";
    public static final String GROUP_EDIT_NAME_LABEL = "group.edit.name.label";
    public static final String GROUP_EDIT_NAME_EMPTY = "group.edit.name.empty";
    public static final String GROUP_EDIT_DESCRIPTION = "group.edit.description";
    public static final String GROUP_EDIT_AUTH_INFO = "group.edit.auth.info";
    public static final String GROUP_EDIT_SCRIPT_INFO = "group.edit.script.info";
    public static final String GROUP_EDIT_VALIDATION_ERROR = "group.edit.validation.error";

    // ============ 功能测试执行结果相关 ============
    public static final String FUNCTIONAL_EXECUTION_RESULTS = "functional.execution.results";
    public static final String FUNCTIONAL_EXECUTION_HISTORY = "functional.execution.history";
    public static final String FUNCTIONAL_EXECUTION_RESULTS_NO_DATA = "functional.execution.results.no_data";
    public static final String FUNCTIONAL_EXECUTION_RESULTS_SUMMARY = "functional.execution.results.summary";
    public static final String FUNCTIONAL_BUTTON_EXPAND_ALL = "functional.button.expand_all";
    public static final String FUNCTIONAL_BUTTON_COLLAPSE_ALL = "functional.button.collapse_all";
    public static final String FUNCTIONAL_TOOLTIP_EXPAND_ALL = "functional.tooltip.expand_all";
    public static final String FUNCTIONAL_TOOLTIP_COLLAPSE_ALL = "functional.tooltip.collapse_all";
    public static final String FUNCTIONAL_TOOLTIP_REFRESH = "functional.tooltip.refresh";
    public static final String FUNCTIONAL_DETAIL_INFO = "functional.detail.info";
    public static final String FUNCTIONAL_TAB_OVERVIEW = "functional.tab.overview";
    public static final String FUNCTIONAL_STATUS_READY = "functional.status.ready";
    public static final String FUNCTIONAL_STATUS_UPDATING = "functional.status.updating";
    public static final String FUNCTIONAL_STATUS_UPDATED = "functional.status.updated";
    public static final String FUNCTIONAL_STATUS_REFRESHING = "functional.status.refreshing";
    public static final String FUNCTIONAL_STATUS_REFRESHED = "functional.status.refreshed";
    public static final String FUNCTIONAL_STATUS_ITERATION_SELECTED = "functional.status.iteration_selected";
    public static final String FUNCTIONAL_STATUS_REQUEST_SELECTED = "functional.status.request_selected";
    public static final String FUNCTIONAL_STATUS_OVERVIEW_SELECTED = "functional.status.overview_selected";

    // ============ 功能测试详情页面相关 ============
    public static final String FUNCTIONAL_DETAIL_OVERVIEW = "functional.detail.overview";
    public static final String FUNCTIONAL_DETAIL_ITERATION = "functional.detail.iteration";
    public static final String FUNCTIONAL_DETAIL_EXECUTION_STATS = "functional.detail.execution_stats";
    public static final String FUNCTIONAL_DETAIL_ITERATION_INFO = "functional.detail.iteration_info";
    public static final String FUNCTIONAL_DETAIL_CSV_DATA = "functional.detail.csv_data";
    public static final String FUNCTIONAL_DETAIL_WELCOME_MESSAGE = "functional.detail.welcome_message";
    public static final String FUNCTIONAL_DETAIL_WELCOME_SUBTITLE = "functional.detail.welcome_subtitle";

    // ============ 功能测试统计相关 ============
    public static final String FUNCTIONAL_STATS_TOTAL_ITERATIONS = "functional.stats.total_iterations";
    public static final String FUNCTIONAL_STATS_TOTAL_REQUESTS = "functional.stats.total_requests";
    public static final String FUNCTIONAL_STATS_TOTAL_TIME = "functional.stats.total_time";
    public static final String FUNCTIONAL_STATS_SUCCESS_RATE = "functional.stats.success_rate";
    public static final String FUNCTIONAL_STATS_START_TIME = "functional.stats.start_time";
    public static final String FUNCTIONAL_STATS_END_TIME = "functional.stats.end_time";
    public static final String FUNCTIONAL_STATS_AVERAGE_TIME = "functional.stats.average_time";
    public static final String FUNCTIONAL_STATS_STATUS = "functional.stats.status";
    public static final String FUNCTIONAL_STATS_STATUS_COMPLETED = "functional.stats.status_completed";

    // ============ 功能测试表格相关 ============
    public static final String FUNCTIONAL_TABLE_ITERATION = "functional.table.iteration";
    public static final String FUNCTIONAL_TABLE_REQUEST_NAME = "functional.table.request_name";
    public static final String FUNCTIONAL_TABLE_METHOD = "functional.table.method";
    public static final String FUNCTIONAL_TABLE_STATUS = "functional.table.status";
    public static final String FUNCTIONAL_TABLE_TIME = "functional.table.time";
    public static final String FUNCTIONAL_TABLE_ASSERTION = "functional.table.assertion";
    public static final String FUNCTIONAL_TABLE_TIMESTAMP = "functional.table.timestamp";

    // ============ 功能测试迭代相关 ============
    public static final String FUNCTIONAL_ITERATION_ROUND = "functional.iteration.round";
    public static final String FUNCTIONAL_ITERATION_ROUND_FORMAT = "functional.iteration.round.format";
    public static final String FUNCTIONAL_ITERATION_START_TIME = "functional.iteration.start_time";
    public static final String FUNCTIONAL_ITERATION_EXECUTION_TIME = "functional.iteration.execution_time";
    public static final String FUNCTIONAL_ITERATION_REQUEST_COUNT = "functional.iteration.request_count";
    public static final String FUNCTIONAL_ITERATION_PASSED_FORMAT = "functional.iteration.passed_format";

    // ============ 线程组相关 ============
    // 线程组模式
    public static final String THREADGROUP_MODE_FIXED = "threadgroup.mode.fixed";
    public static final String THREADGROUP_MODE_RAMP_UP = "threadgroup.mode.ramp_up";
    public static final String THREADGROUP_MODE_SPIKE = "threadgroup.mode.spike";
    public static final String THREADGROUP_MODE_STAIRS = "threadgroup.mode.stairs";

    // 线程组界面标签
    public static final String THREADGROUP_MODE_LABEL = "threadgroup.mode.label";
    public static final String THREADGROUP_PREVIEW_TITLE = "threadgroup.preview.title";

    // 固定模式标签
    public static final String THREADGROUP_FIXED_USERS = "threadgroup.fixed.users";
    public static final String THREADGROUP_FIXED_EXECUTION_MODE = "threadgroup.fixed.execution_mode";
    public static final String THREADGROUP_FIXED_USE_TIME = "threadgroup.fixed.use_time";
    public static final String THREADGROUP_FIXED_LOOPS = "threadgroup.fixed.loops";
    public static final String THREADGROUP_FIXED_DURATION = "threadgroup.fixed.duration";

    // 递增模式标签
    public static final String THREADGROUP_RAMPUP_START_USERS = "threadgroup.rampup.start_users";
    public static final String THREADGROUP_RAMPUP_END_USERS = "threadgroup.rampup.end_users";
    public static final String THREADGROUP_RAMPUP_RAMP_TIME = "threadgroup.rampup.ramp_time";
    public static final String THREADGROUP_RAMPUP_TEST_DURATION = "threadgroup.rampup.test_duration";

    // 尖刺模式标签
    public static final String THREADGROUP_SPIKE_MIN_USERS = "threadgroup.spike.min_users";
    public static final String THREADGROUP_SPIKE_MAX_USERS = "threadgroup.spike.max_users";
    public static final String THREADGROUP_SPIKE_RAMP_UP_TIME = "threadgroup.spike.ramp_up_time";
    public static final String THREADGROUP_SPIKE_HOLD_TIME = "threadgroup.spike.hold_time";
    public static final String THREADGROUP_SPIKE_RAMP_DOWN_TIME = "threadgroup.spike.ramp_down_time";
    public static final String THREADGROUP_SPIKE_TEST_DURATION = "threadgroup.spike.test_duration";

    // 阶梯模式标签
    public static final String THREADGROUP_STAIRS_START_USERS = "threadgroup.stairs.start_users";
    public static final String THREADGROUP_STAIRS_END_USERS = "threadgroup.stairs.end_users";
    public static final String THREADGROUP_STAIRS_STEP_SIZE = "threadgroup.stairs.step_size";
    public static final String THREADGROUP_STAIRS_HOLD_TIME = "threadgroup.stairs.hold_time";
    public static final String THREADGROUP_STAIRS_TEST_DURATION = "threadgroup.stairs.test_duration";

    // 预览面板标签
    public static final String THREADGROUP_PREVIEW_TIME_SECONDS = "threadgroup.preview.time_seconds";
    public static final String THREADGROUP_PREVIEW_MODE_PREFIX = "threadgroup.preview.mode_prefix";

    // ============ Settings Dialog related ============
    // Dialog title and labels
    public static final String SETTINGS_DIALOG_TITLE = "settings.dialog.title";
    public static final String SETTINGS_DIALOG_SAVE = "settings.dialog.save";
    public static final String SETTINGS_DIALOG_CANCEL = "settings.dialog.cancel";
    public static final String SETTINGS_DIALOG_APPLY = "settings.dialog.apply";

    // Unsaved changes warnings
    public static final String SETTINGS_UNSAVED_CHANGES_WARNING = "settings.unsaved_changes.warning";
    public static final String SETTINGS_DISCARD_CHANGES = "settings.discard_changes";
    public static final String SETTINGS_SAVE_NOW = "settings.save_now";
    public static final String SETTINGS_CONFIRM_DISCARD_TITLE = "settings.confirm_discard.title";
    public static final String SETTINGS_CONFIRM_DISCARD_MESSAGE = "settings.confirm_discard.message";
    public static final String SETTINGS_RESET_TO_DEFAULT = "settings.reset_to_default";

    // Request settings section
    public static final String SETTINGS_REQUEST_TITLE = "settings.request.title";
    public static final String SETTINGS_REQUEST_MAX_BODY_SIZE = "settings.request.max_body_size";
    public static final String SETTINGS_REQUEST_MAX_BODY_SIZE_TOOLTIP = "settings.request.max_body_size.tooltip";
    public static final String SETTINGS_REQUEST_TIMEOUT = "settings.request.timeout";
    public static final String SETTINGS_REQUEST_TIMEOUT_TOOLTIP = "settings.request.timeout.tooltip";
    public static final String SETTINGS_REQUEST_MAX_DOWNLOAD_SIZE = "settings.request.max_download_size";
    public static final String SETTINGS_REQUEST_MAX_DOWNLOAD_SIZE_TOOLTIP = "settings.request.max_download_size.tooltip";
    public static final String SETTINGS_REQUEST_FOLLOW_REDIRECTS_TOOLTIP = "settings.request.follow_redirects.tooltip";
    public static final String SETTINGS_REQUEST_FOLLOW_REDIRECTS_CHECKBOX = "settings.request.follow_redirects.checkbox";
    public static final String SETTINGS_REQUEST_SSL_VERIFICATION_TOOLTIP = "settings.request.ssl_verification.tooltip";
    public static final String SETTINGS_REQUEST_SSL_VERIFICATION_CHECKBOX = "settings.request.ssl_verification.checkbox";

    // JMeter settings section
    public static final String SETTINGS_JMETER_TITLE = "settings.jmeter.title";
    public static final String SETTINGS_JMETER_MAX_IDLE = "settings.jmeter.max_idle";
    public static final String SETTINGS_JMETER_MAX_IDLE_TOOLTIP = "settings.jmeter.max_idle.tooltip";
    public static final String SETTINGS_JMETER_KEEP_ALIVE = "settings.jmeter.keep_alive";
    public static final String SETTINGS_JMETER_KEEP_ALIVE_TOOLTIP = "settings.jmeter.keep_alive.tooltip";

    // Download settings section
    public static final String SETTINGS_DOWNLOAD_TITLE = "settings.download.title";
    public static final String SETTINGS_DOWNLOAD_SHOW_PROGRESS = "settings.download.show_progress";
    public static final String SETTINGS_DOWNLOAD_SHOW_PROGRESS_TOOLTIP = "settings.download.show_progress.tooltip";
    public static final String SETTINGS_DOWNLOAD_THRESHOLD = "settings.download.threshold";
    public static final String SETTINGS_DOWNLOAD_THRESHOLD_TOOLTIP = "settings.download.threshold.tooltip";

    // General settings section
    public static final String SETTINGS_GENERAL_TITLE = "settings.general.title";
    public static final String SETTINGS_GENERAL_MAX_HISTORY = "settings.general.max_history";
    public static final String SETTINGS_GENERAL_MAX_HISTORY_TOOLTIP = "settings.general.max_history.tooltip";
    public static final String SETTINGS_GENERAL_MAX_OPENED_REQUESTS = "settings.general.max_opened_requests";
    public static final String SETTINGS_GENERAL_MAX_OPENED_REQUESTS_TOOLTIP = "settings.general.max_opened_requests.tooltip";
    public static final String SETTINGS_GENERAL_AUTO_FORMAT_RESPONSE = "settings.general.auto_format_response";
    public static final String SETTINGS_GENERAL_AUTO_FORMAT_RESPONSE_TOOLTIP = "settings.general.auto_format_response.tooltip";
    public static final String SETTINGS_GENERAL_SIDEBAR_EXPANDED = "settings.general.sidebar_expanded";
    public static final String SETTINGS_GENERAL_SIDEBAR_EXPANDED_TOOLTIP = "settings.general.sidebar_expanded.tooltip";

    // Validation messages
    public static final String SETTINGS_VALIDATION_ERROR_MESSAGE = "settings.validation.error.message";
    public static final String SETTINGS_VALIDATION_MAX_BODY_SIZE_ERROR = "settings.validation.max_body_size.error";
    public static final String SETTINGS_VALIDATION_TIMEOUT_ERROR = "settings.validation.timeout.error";
    public static final String SETTINGS_VALIDATION_MAX_DOWNLOAD_SIZE_ERROR = "settings.validation.max_download_size.error";
    public static final String SETTINGS_VALIDATION_MAX_IDLE_ERROR = "settings.validation.max_idle.error";
    public static final String SETTINGS_VALIDATION_KEEP_ALIVE_ERROR = "settings.validation.keep_alive.error";
    public static final String SETTINGS_VALIDATION_THRESHOLD_ERROR = "settings.validation.threshold.error";
    public static final String SETTINGS_VALIDATION_MAX_HISTORY_ERROR = "settings.validation.max_history.error";
    public static final String SETTINGS_VALIDATION_MAX_OPENED_REQUESTS_ERROR = "settings.validation.max_opened_requests_error";
    public static final String SETTINGS_VALIDATION_PORT_ERROR = "settings.validation.port.error";
    public static final String SETTINGS_VALIDATION_UPDATE_INTERVAL_ERROR = "settings.validation.update_interval.error";
    public static final String SETTINGS_VALIDATION_STARTUP_DELAY_ERROR = "settings.validation.startup_delay.error";

    // Success messages
    public static final String SETTINGS_SAVE_SUCCESS_MESSAGE = "settings.save.success.message";

    // Error messages
    public static final String SETTINGS_SAVE_ERROR_MESSAGE = "settings.save.error.message";

    // ============ 请求Body相关 ============
    public static final String REQUEST_BODY_TYPE = "request.body.type";
    public static final String REQUEST_BODY_FORMAT = "request.body.format";
    public static final String REQUEST_BODY_NONE = "request.body.none";
    public static final String REQUEST_BODY_FORMAT_ONLY_RAW = "request.body.format.only_raw";
    public static final String REQUEST_BODY_FORMAT_EMPTY = "request.body.format.empty";

    // ============ 响应头面板相关 ============
    public static final String RESPONSE_HEADERS_COPY_SELECTED = "response.headers.copy_selected";
    public static final String RESPONSE_HEADERS_COPY_CELL = "response.headers.copy_cell";
    public static final String RESPONSE_HEADERS_COPY_ALL = "response.headers.copy_all";
    public static final String RESPONSE_HEADERS_SELECT_ALL = "response.headers.select_all";

    // ============ CSV Data Panel related ============
    public static final String CSV_STATUS_NO_DATA = "csv.status.no_data";
    public static final String CSV_STATUS_LOADED = "csv.status.loaded";
    public static final String CSV_MANUAL_CREATED = "csv.manual_created";
    public static final String CSV_BUTTON_CLEAR_TOOLTIP = "csv.button.clear.tooltip";
    public static final String CSV_MENU_IMPORT_FILE = "csv.menu.import_file";
    public static final String CSV_MENU_CREATE_MANUAL = "csv.menu.create_manual";
    public static final String CSV_MENU_MANAGE_DATA = "csv.menu.manage_data";
    public static final String CSV_MENU_CLEAR_DATA = "csv.menu.clear_data";
    public static final String CSV_DATA_CLEARED = "csv.data.cleared";
    public static final String CSV_DIALOG_MANAGEMENT_TITLE = "csv.dialog.management.title";
    public static final String CSV_DATA_DRIVEN_TEST = "csv.data_driven_test";
    public static final String CSV_DIALOG_DESCRIPTION = "csv.dialog.description";
    public static final String CSV_CURRENT_STATUS = "csv.current_status";
    public static final String CSV_OPERATIONS = "csv.operations";
    public static final String CSV_BUTTON_SELECT_FILE = "csv.button.select_file";
    public static final String CSV_BUTTON_MANAGE_DATA = "csv.button.manage_data";
    public static final String CSV_BUTTON_CLEAR_DATA = "csv.button.clear_data";
    public static final String CSV_NO_MANAGEABLE_DATA = "csv.no_manageable_data";
    public static final String CSV_DATA_MANAGEMENT = "csv.data_management";
    public static final String CSV_DATA_SOURCE_INFO = "csv.data_source_info";
    public static final String CSV_BUTTON_ADD_ROW = "csv.button.add_row";
    public static final String CSV_BUTTON_DELETE_ROW = "csv.button.delete_row";
    public static final String CSV_BUTTON_ADD_COLUMN = "csv.button.add_column";
    public static final String CSV_BUTTON_DELETE_COLUMN = "csv.button.delete_column";
    public static final String CSV_SELECT_ROWS_TO_DELETE = "csv.select_rows_to_delete";
    public static final String CSV_CONFIRM_DELETE_ROWS = "csv.confirm_delete_rows";
    public static final String CSV_CONFIRM_DELETE = "csv.confirm_delete";
    public static final String CSV_ENTER_COLUMN_NAME = "csv.enter_column_name";
    public static final String CSV_ADD_COLUMN = "csv.add_column";
    public static final String CSV_SELECT_COLUMNS_TO_DELETE = "csv.select_columns_to_delete";
    public static final String CSV_CANNOT_DELETE_ALL_COLUMNS = "csv.cannot_delete_all_columns";
    public static final String CSV_CONFIRM_DELETE_COLUMNS = "csv.confirm_delete_columns";
    public static final String CSV_USAGE_INSTRUCTIONS = "csv.usage_instructions";
    public static final String CSV_USAGE_TEXT = "csv.usage_text";
    public static final String CSV_NO_VALID_DATA_ROWS = "csv.no_valid_data_rows";
    public static final String CSV_DATA_SAVED = "csv.data_saved";
    public static final String CSV_SAVE_SUCCESS = "csv.save_success";
    public static final String CSV_SAVE_FAILED = "csv.save_failed";
    public static final String CSV_SELECT_FILE = "csv.select_file";
    public static final String CSV_FILE_FILTER = "csv.file_filter";
    public static final String CSV_FILE_VALIDATION_FAILED = "csv.file_validation_failed";
    public static final String CSV_NO_VALID_DATA = "csv.no_valid_data";
    public static final String CSV_LOAD_FAILED = "csv.load_failed";
    public static final String CSV_FILE_NOT_EXIST = "csv.file_not_exist";
    public static final String CSV_FILE_NOT_VALID = "csv.file_not_valid";
    public static final String CSV_FILE_NOT_CSV = "csv.file_not_csv";
    public static final String CSV_CREATE_MANUAL_DIALOG_TITLE = "csv.create_manual.dialog_title";
    public static final String CSV_CREATE_MANUAL_DESCRIPTION = "csv.create_manual.description";
    public static final String CSV_CREATE_MANUAL_COLUMN_COUNT = "csv.create_manual.column_count";
    public static final String CSV_CREATE_MANUAL_ROW_COUNT = "csv.create_manual.row_count";
    public static final String CSV_CREATE_MANUAL_COLUMN_HEADERS = "csv.create_manual.column_headers";
    public static final String CSV_CREATE_MANUAL_INVALID_COLUMN_COUNT = "csv.create_manual.invalid_column_count";
    public static final String CSV_CREATE_MANUAL_INVALID_ROW_COUNT = "csv.create_manual.invalid_row_count";
    public static final String CSV_CREATE_MANUAL_HEADERS_MISMATCH = "csv.create_manual.headers_mismatch";

    // ============ OkHttpResponseHandler ============
    public static final String DOWNLOAD_PROGRESS_TITLE = "download.progress.title";
    public static final String DOWNLOAD_CANCELLED = "download.cancelled";
    public static final String BINARY_TOO_LARGE = "binary.too.large";
    public static final String BINARY_TOO_LARGE_BODY = "binary.too.large.body";
    public static final String BINARY_SAVED_TEMP_FILE = "binary.saved_temp_file";
    public static final String NO_RESPONSE_BODY = "no.response.body";
    public static final String DOWNLOAD_LIMIT_TITLE = "download.limit.title";
    public static final String TEXT_TOO_LARGE = "text.too_large";
    public static final String TEXT_TOO_LARGE_BODY = "text.too_large.body";
    public static final String BODY_TOO_LARGE_SAVED = "body.too_large.saved";
    public static final String SSE_STREAM_UNSUPPORTED = "sse.stream.unsupported";

    // ============ ResponseAssertion 国际化 ============
    public static final String RESPONSE_ASSERTION_STATUS_FAILED = "response.assertion.status_failed";
    public static final String RESPONSE_ASSERTION_HEADER_NOT_FOUND = "response.assertion.header_not_found";
    public static final String RESPONSE_ASSERTION_HEADER_NOT_FOUND_WITH_NAME = "response.assertion.header_not_found_with_name";
    public static final String RESPONSE_ASSERTION_BELOW_FAILED = "response.assertion.below_failed";
    public static final String RESPONSE_ASSERTION_INVALID_JSON = "response.assertion.invalid_json";

    // ============ Expectation 国际化 ============
    public static final String EXPECTATION_INCLUDE_FAILED = "expectation.include_failed";
    public static final String EXPECTATION_EQL_FAILED = "expectation.eql_failed";
    public static final String EXPECTATION_PROPERTY_NOT_FOUND = "expectation.property_not_found";
    public static final String EXPECTATION_PROPERTY_NOT_MAP = "expectation.property_not_map";
    public static final String EXPECTATION_MATCH_REGEX_FAILED = "expectation.match_regex_failed";
    public static final String EXPECTATION_MATCH_PATTERN_FAILED = "expectation.match_pattern_failed";
    public static final String EXPECTATION_MATCH_JSREGEXP_FAILED = "expectation.match_jsregexp_failed";
    public static final String EXPECTATION_BELOW_FAILED = "expectation.below_failed";

    // ============ ScriptPanel AutoCompletion 国际化 ============
    public static final String AUTOCOMPLETE_PM = "autocomplete.pm";
    public static final String AUTOCOMPLETE_POSTMAN = "autocomplete.postman";
    public static final String AUTOCOMPLETE_REQUEST = "autocomplete.request";
    public static final String AUTOCOMPLETE_RESPONSE = "autocomplete.response";
    public static final String AUTOCOMPLETE_ENV = "autocomplete.env";
    public static final String AUTOCOMPLETE_RESPONSE_BODY = "autocomplete.response_body";
    public static final String AUTOCOMPLETE_RESPONSE_HEADERS = "autocomplete.response_headers";
    public static final String AUTOCOMPLETE_STATUS = "autocomplete.status";
    public static final String AUTOCOMPLETE_STATUS_CODE = "autocomplete.status_code";
    public static final String AUTOCOMPLETE_SET_ENV = "autocomplete.set_env";
    public static final String AUTOCOMPLETE_GET_ENV = "autocomplete.get_env";
    public static final String AUTOCOMPLETE_IF = "autocomplete.if";
    public static final String AUTOCOMPLETE_ELSE = "autocomplete.else";
    public static final String AUTOCOMPLETE_FOR = "autocomplete.for";
    public static final String AUTOCOMPLETE_WHILE = "autocomplete.while";
    public static final String AUTOCOMPLETE_FUNCTION = "autocomplete.function";
    public static final String AUTOCOMPLETE_RETURN = "autocomplete.return";
    public static final String AUTOCOMPLETE_SNIPPET_SET_ENV = "autocomplete.snippet.set_env";
    public static final String AUTOCOMPLETE_SNIPPET_GET_ENV = "autocomplete.snippet.get_env";
    public static final String AUTOCOMPLETE_SNIPPET_BTOA = "autocomplete.snippet.btoa";
    public static final String AUTOCOMPLETE_SNIPPET_ATOB = "autocomplete.snippet.atob";
    public static final String AUTOCOMPLETE_SNIPPET_ENCODE_URI = "autocomplete.snippet.encode_uri";
    public static final String AUTOCOMPLETE_SNIPPET_DECODE_URI = "autocomplete.snippet.decode_uri";
    public static final String AUTOCOMPLETE_SNIPPET_CONSOLE_LOG = "autocomplete.snippet.console_log";
    public static final String AUTOCOMPLETE_SNIPPET_JSON_PARSE = "autocomplete.snippet.json_parse";
    public static final String AUTOCOMPLETE_SNIPPET_JSON_STRINGIFY = "autocomplete.snippet.json_stringify";

    // ============ 工作区远程配置相关 ============
    public static final String WORKSPACE_REMOTE_CONFIG_TITLE = "workspace.remote.config.title";
    public static final String WORKSPACE_CONFIG_PROGRESS = "workspace.config.progress";
    public static final String WORKSPACE_CONFIG_PROGRESS_START = "workspace.config.progress.start";
    public static final String WORKSPACE_CONFIG_PROGRESS_VALIDATING = "workspace.config.progress.validating";
    public static final String WORKSPACE_CONFIG_PROGRESS_DONE = "workspace.config.progress.done";
    public static final String WORKSPACE_CONFIG_PROGRESS_FAILED = "workspace.config.progress.failed";
    public static final String WORKSPACE_VALIDATION_GIT_URL_INVALID = "workspace.validation.git.url.invalid";

    // ============ 自动更新设置相关 ============
    public static final String SETTINGS_AUTO_UPDATE_TITLE = "settings.auto_update.title";
    public static final String SETTINGS_AUTO_UPDATE_ENABLED_TOOLTIP = "settings.auto_update.enabled.tooltip";
    public static final String SETTINGS_AUTO_UPDATE_ENABLED_CHECKBOX = "settings.auto_update.enabled.checkbox";
    public static final String SETTINGS_AUTO_UPDATE_INTERVAL = "settings.auto_update.interval";
    public static final String SETTINGS_AUTO_UPDATE_INTERVAL_TOOLTIP = "settings.auto_update.interval.tooltip";
    public static final String SETTINGS_AUTO_UPDATE_STARTUP_DELAY = "settings.auto_update.startup_delay";
    public static final String SETTINGS_AUTO_UPDATE_STARTUP_DELAY_TOOLTIP = "settings.auto_update.startup_delay.tooltip";
    public static final String SETTINGS_UPDATE_SOURCE_PREFERENCE = "settings.update_source.preference";
    public static final String SETTINGS_UPDATE_SOURCE_PREFERENCE_TOOLTIP = "settings.update_source.preference.tooltip";
    public static final String SETTINGS_UPDATE_SOURCE_AUTO = "settings.update_source.auto";
    public static final String SETTINGS_UPDATE_SOURCE_GITHUB = "settings.update_source.github";
    public static final String SETTINGS_UPDATE_SOURCE_GITEE = "settings.update_source.gitee";

    // ============ UI设置相关 ============

    // WebSocket面板相关
    public static final String WEBSOCKET_PANEL_LABEL_SEND_MESSAGE = "websocket.panel.label.send_message";
    public static final String WEBSOCKET_PANEL_CHECKBOX_CLEAR = "websocket.panel.checkbox.clear";
    public static final String WEBSOCKET_PANEL_LABEL_TIMEOUT = "websocket.panel.label.timeout";
    public static final String WEBSOCKET_PANEL_BUTTON_START = "websocket.panel.button.start";
    public static final String WEBSOCKET_PANEL_BUTTON_STOP = "websocket.panel.button.stop";
    public static final String WEBSOCKET_PANEL_BUTTON_SEND = "websocket.panel.button.send";

    // ============ WebSocket面板相关 ============
    public static final String WEBSOCKET_COLUMN_TYPE = "websocket.column.type";
    public static final String WEBSOCKET_COLUMN_TIME = "websocket.column.time";
    public static final String WEBSOCKET_COLUMN_CONTENT = "websocket.column.content";
    public static final String WEBSOCKET_TYPE_ALL = "websocket.type.all";
    public static final String WEBSOCKET_TYPE_SENT = "websocket.type.sent";
    public static final String WEBSOCKET_TYPE_RECEIVED = "websocket.type.received";
    public static final String WEBSOCKET_TYPE_CONNECTED = "websocket.type.connected";
    public static final String WEBSOCKET_TYPE_CLOSED = "websocket.type.closed";
    public static final String WEBSOCKET_TYPE_WARNING = "websocket.type.warning";
    public static final String WEBSOCKET_TYPE_INFO = "websocket.type.info";
    public static final String WEBSOCKET_TYPE_BINARY = "websocket.type.binary";
    public static final String BUTTON_CLEAR_MESSAGES = "button.clear_messages";
    public static final String BUTTON_COPY = "button.copy";
    public static final String BUTTON_DETAIL = "button.detail";
    public static final String BUTTON_FORMAT = "button.format";
    public static final String BUTTON_RAW = "button.raw";
    public static final String WEBSOCKET_DIALOG_TITLE = "websocket.dialog.title";

    // ============ PLUS_PANEL_HINT ============
    public static final String PLUS_PANEL_HINT = "plus.panel.hint";

    // ============ 代码片段弹窗（SnippetDialog）相关 ============
    public static final String SNIPPET_DIALOG_TITLE = "snippet.dialog.title";
    public static final String SNIPPET_DIALOG_PREVIEW_TITLE = "snippet.dialog.preview.title";
    public static final String SNIPPET_DIALOG_INSERT = "snippet.dialog.insert";
    public static final String SNIPPET_DIALOG_CLOSE = "snippet.dialog.close";
    public static final String SNIPPET_DIALOG_SELECT_SNIPPET_FIRST = "snippet.dialog.select_snippet_first";
    public static final String SNIPPET_DIALOG_TIP = "snippet.dialog.tip";
    public static final String SNIPPET_DIALOG_CATEGORY_ALL = "snippet.dialog.category.all";
    public static final String SNIPPET_DIALOG_CATEGORY_PRE_SCRIPT = "snippet.dialog.category.pre_script";
    public static final String SNIPPET_DIALOG_CATEGORY_ASSERT = "snippet.dialog.category.assert";
    public static final String SNIPPET_DIALOG_CATEGORY_EXTRACT = "snippet.dialog.category.extract";
    public static final String SNIPPET_DIALOG_CATEGORY_LOCAL_VAR = "snippet.dialog.category.local_var";
    public static final String SNIPPET_DIALOG_CATEGORY_ENV_VAR = "snippet.dialog.category.env_var";
    public static final String SNIPPET_DIALOG_CATEGORY_ENCRYPT = "snippet.dialog.category.encrypt";
    public static final String SNIPPET_DIALOG_CATEGORY_ENCODE = "snippet.dialog.category.encode";
    public static final String SNIPPET_DIALOG_CATEGORY_STRING = "snippet.dialog.category.string";
    public static final String SNIPPET_DIALOG_CATEGORY_ARRAY = "snippet.dialog.category.array";
    public static final String SNIPPET_DIALOG_CATEGORY_JSON = "snippet.dialog.category.json";
    public static final String SNIPPET_DIALOG_CATEGORY_DATE = "snippet.dialog.category.date";
    public static final String SNIPPET_DIALOG_CATEGORY_REGEX = "snippet.dialog.category.regex";
    public static final String SNIPPET_DIALOG_CATEGORY_LOG = "snippet.dialog.category.log";
    public static final String SNIPPET_DIALOG_CATEGORY_CONTROL = "snippet.dialog.category.control";
    public static final String SNIPPET_DIALOG_CATEGORY_TOKEN = "snippet.dialog.category.token";
    public static final String SNIPPET_DIALOG_CATEGORY_OTHER = "snippet.dialog.category.other";
    public static final String SNIPPET_DIALOG_NOT_FOUND = "snippet.dialog.not_found";

    // ============ WaterfallChartPanel 国际化 ============
    public static final String WATERFALL_HTTP_VERSION = "waterfall.http_version";
    public static final String WATERFALL_LOCAL_ADDRESS = "waterfall.local_address";
    public static final String WATERFALL_REMOTE_ADDRESS = "waterfall.remote_address";
    public static final String WATERFALL_TLS_PROTOCOL = "waterfall.tls_protocol";
    public static final String WATERFALL_CIPHER_NAME = "waterfall.cipher_name";
    public static final String WATERFALL_CERTIFICATE_CN = "waterfall.certificate_cn";
    public static final String WATERFALL_ISSUER_CN = "waterfall.issuer_cn";
    public static final String WATERFALL_VALID_UNTIL = "waterfall.valid_until";
    public static final String WATERFALL_STAGE_DNS = "waterfall.stage.dns";
    public static final String WATERFALL_STAGE_SOCKET = "waterfall.stage.socket";
    public static final String WATERFALL_STAGE_SSL = "waterfall.stage.ssl";
    public static final String WATERFALL_STAGE_REQUEST_SEND = "waterfall.stage.request_send";
    public static final String WATERFALL_STAGE_WAITING = "waterfall.stage.waiting";
    public static final String WATERFALL_STAGE_CONTENT_DOWNLOAD = "waterfall.stage.content_download";
    public static final String WATERFALL_STAGE_DESC_DNS = "waterfall.stage.desc.dns";
    public static final String WATERFALL_STAGE_DESC_SOCKET = "waterfall.stage.desc.socket";
    public static final String WATERFALL_STAGE_DESC_SSL = "waterfall.stage.desc.ssl";
    public static final String WATERFALL_STAGE_DESC_REQUEST_SEND = "waterfall.stage.desc.request_send";
    public static final String WATERFALL_STAGE_DESC_WAITING = "waterfall.stage.desc.waiting";
    public static final String WATERFALL_STAGE_DESC_CONTENT_DOWNLOAD = "waterfall.stage.desc.content_download";

    // ============ 操作相关 ============
    public static final String WORKSPACE_OPERATION_SUCCESS = "workspace.operation.success";
    public static final String WORKSPACE_OPERATION_COMPLETED_CLOSING = "workspace.operation.completed_closing";
    public static final String WORKSPACE_OPERATION_FAILED = "workspace.operation.failed";
    public static final String WORKSPACE_OPERATION_FAILED_TIP = "workspace.operation.failed_tip";
    public static final String WORKSPACE_OPERATION_FAILED_DETAIL = "workspace.operation.failed_detail";

    // ============ GitOperationDialog 国际化 ============
    public static final String GIT_DIALOG_WORKSPACE = "git.dialog.workspace";
    public static final String GIT_DIALOG_CURRENT_BRANCH = "git.dialog.currentBranch";
    public static final String GIT_DIALOG_UNKNOWN = "git.dialog.unknown";
    public static final String GIT_DIALOG_REMOTE_BRANCH = "git.dialog.remoteBranch";
    public static final String GIT_DIALOG_NOT_SET = "git.dialog.notSet";
    public static final String GIT_DIALOG_STATUS_CHECK = "git.dialog.statusCheck";
    public static final String GIT_DIALOG_CHECKING_STATUS = "git.dialog.checkingStatus";
    public static final String GIT_DIALOG_FILE_CHANGES = "git.dialog.fileChanges";
    public static final String GIT_DIALOG_LOADING_FILE_CHANGES = "git.dialog.loadingFileChanges";
    public static final String GIT_DIALOG_COMMIT_MESSAGE = "git.dialog.commitMessage";
    public static final String GIT_DIALOG_DEFAULT_COMMIT_MESSAGE = "git.dialog.defaultCommitMessage";
    public static final String GIT_DIALOG_CHECKING_STATUS_AND_CONFLICT = "git.dialog.checkingStatusAndConflict";
    public static final String GIT_DIALOG_STATUS_CHECK_DONE = "git.dialog.statusCheckDone";
    public static final String GIT_DIALOG_STATUS_CHECK_FAILED = "git.dialog.statusCheckFailed";
    public static final String GIT_DIALOG_STATUS_SUMMARY = "git.dialog.statusSummary";
    public static final String GIT_DIALOG_HAS_UNCOMMITTED_CHANGES = "git.dialog.hasUncommittedChanges";
    public static final String GIT_DIALOG_HAS_LOCAL_COMMITS = "git.dialog.hasLocalCommits";
    public static final String GIT_DIALOG_HAS_REMOTE_COMMITS = "git.dialog.hasRemoteCommits";
    public static final String GIT_DIALOG_LOCAL_AHEAD = "git.dialog.localAhead";
    public static final String GIT_DIALOG_REMOTE_AHEAD = "git.dialog.remoteAhead";
    public static final String GIT_DIALOG_WARNINGS = "git.dialog.warnings";
    public static final String GIT_DIALOG_SUGGESTIONS = "git.dialog.suggestions";
    public static final String GIT_DIALOG_YES = "git.dialog.yes";
    public static final String GIT_DIALOG_NO = "git.dialog.no";

    // ============ GitOperation 名称国际化 ============
    public static final String GIT_OPERATION_COMMIT = "git.operation.commit";
    public static final String GIT_OPERATION_PUSH = "git.operation.push";
    public static final String GIT_OPERATION_PULL = "git.operation.pull";

    // ============ GitOperationDialog 选项相关国际化 ============
    public static final String GIT_DIALOG_OPTION_COMMIT_TITLE = "git.dialog.option.commit.title";
    public static final String GIT_DIALOG_OPTION_COMMIT_FIRST = "git.dialog.option.commit_first";
    public static final String GIT_DIALOG_OPTION_COMMIT_FIRST_DESC = "git.dialog.option.commit_first.desc";
    public static final String GIT_DIALOG_OPTION_COMMIT_AND_PUSH = "git.dialog.option.commit_and_push";
    public static final String GIT_DIALOG_OPTION_COMMIT_AND_PUSH_DESC = "git.dialog.option.commit_and_push.desc";
    public static final String GIT_DIALOG_OPTION_PULL_CONFLICT_TITLE = "git.dialog.option.pull.conflict_title";
    public static final String GIT_DIALOG_OPTION_CANCEL = "git.dialog.option.cancel";
    public static final String GIT_DIALOG_OPTION_CANCEL_DESC = "git.dialog.option.cancel.desc";
    public static final String GIT_DIALOG_OPTION_FORCE_PULL = "git.dialog.option.force_pull";
    public static final String GIT_DIALOG_OPTION_FORCE_PULL_DESC = "git.dialog.option.force_pull.desc";
    // 步骤指示器相关
    public static final String STEP_CHECK_STATUS = "step.checkStatus";
    public static final String STEP_CONFIRM_CHANGE = "step.confirmChange";
    public static final String STEP_SELECT_STRATEGY = "step.selectStrategy";
    public static final String STEP_EXECUTE_OPERATION = "step.executeOperation";

    // ============ GitOperationDialog 缺失的国际化键 ============
    // Pull 操作相关选项
    public static final String GIT_DIALOG_OPTION_PULL_UNCOMMITTED_AUTO_MERGE_TITLE = "git.dialog.option.pull.uncommittedAutoMerge.title";
    public static final String GIT_DIALOG_OPTION_PULL_UNCOMMITTED_CHOOSE_TITLE = "git.dialog.option.pull.uncommittedChoose.title";
    public static final String GIT_DIALOG_OPTION_COMMIT_FIRST_PULL = "git.dialog.option.commitFirstPull";
    public static final String GIT_DIALOG_OPTION_COMMIT_FIRST_PULL_AUTO_MERGE_DESC = "git.dialog.option.commitFirstPull.autoMergeDesc";
    public static final String GIT_DIALOG_OPTION_FORCE_PULL_DISCARD = "git.dialog.option.forcePullDiscard";
    public static final String GIT_DIALOG_OPTION_FORCE_PULL_DISCARD_WARNING_DESC = "git.dialog.option.forcePullDiscard.warningDesc";
    public static final String GIT_DIALOG_OPTION_COMMIT_FIRST_PULL_KEEP_DESC = "git.dialog.option.commitFirstPull.keepDesc";
    public static final String GIT_DIALOG_OPTION_STASH_PULL = "git.dialog.option.stashPull";
    public static final String GIT_DIALOG_OPTION_STASH_PULL_DESC = "git.dialog.option.stashPull.desc";
    public static final String GIT_DIALOG_OPTION_FORCE_PULL_LOSE_DESC = "git.dialog.option.forcePull.loseDesc";

    // Push 操作相关选项
    public static final String GIT_DIALOG_OPTION_PUSH_CONFLICT_TITLE = "git.dialog.option.push.conflictTitle";
    public static final String GIT_DIALOG_OPTION_CANCEL_EXTERNAL_TOOL = "git.dialog.option.cancelExternalTool";
    public static final String GIT_DIALOG_OPTION_CANCEL_EXTERNAL_TOOL_DESC = "git.dialog.option.cancelExternalTool.desc";
    public static final String GIT_DIALOG_OPTION_FORCE_PUSH_OVERWRITE = "git.dialog.option.forcePushOverwrite";
    public static final String GIT_DIALOG_OPTION_FORCE_PUSH_OVERWRITE_COMMITS_DESC = "git.dialog.option.forcePushOverwrite.commitsDesc";
    public static final String GIT_DIALOG_OPTION_PUSH_REMOTE_AUTO_MERGE_TITLE = "git.dialog.option.push.remoteAutoMerge.title";
    public static final String GIT_DIALOG_OPTION_PULL_FIRST_PUSH = "git.dialog.option.pullFirstPush";
    public static final String GIT_DIALOG_OPTION_PULL_FIRST_PUSH_DESC = "git.dialog.option.pullFirstPush.desc";
    public static final String GIT_DIALOG_OPTION_FORCE_PUSH_OVERWRITE_REMOTE_DESC = "git.dialog.option.forcePushOverwrite.remoteDesc";
    public static final String GIT_DIALOG_OPTION_PUSH_REMOTE_CHOOSE_TITLE = "git.dialog.option.push.remoteChoose.title";

    // 文件变更相关
    public static final String GIT_DIALOG_FILE_CHANGES_NOT_AVAILABLE = "git.dialog.fileChanges.notAvailable";
    public static final String GIT_DIALOG_LOCAL_CHANGES_TITLE = "git.dialog.localChanges.title";
    public static final String GIT_DIALOG_REMOTE_CHANGES_TITLE = "git.dialog.remoteChanges.title";
    public static final String GIT_DIALOG_ADDED_FILES = "git.dialog.addedFiles";
    public static final String GIT_DIALOG_CHANGED_FILES = "git.dialog.changedFiles";
    public static final String GIT_DIALOG_MODIFIED_FILES = "git.dialog.modifiedFiles";
    public static final String GIT_DIALOG_REMOVED_FILES = "git.dialog.removedFiles";
    public static final String GIT_DIALOG_MISSING_FILES = "git.dialog.missingFiles";
    public static final String GIT_DIALOG_UNTRACKED_FILES = "git.dialog.untrackedFiles";
    public static final String GIT_DIALOG_CONFLICTING_FILES = "git.dialog.conflictingFiles";
    public static final String GIT_DIALOG_NO_LOCAL_CHANGES = "git.dialog.noLocalChanges";
    public static final String GIT_DIALOG_REMOTE_ADDED_FILES = "git.dialog.remoteAddedFiles";
    public static final String GIT_DIALOG_REMOTE_MODIFIED_FILES = "git.dialog.remoteModifiedFiles";
    public static final String GIT_DIALOG_REMOTE_REMOVED_FILES = "git.dialog.remoteRemovedFiles";
    public static final String GIT_DIALOG_REMOTE_RENAMED_FILES = "git.dialog.remoteRenamedFiles";
    public static final String GIT_DIALOG_REMOTE_COPIED_FILES = "git.dialog.remoteCopiedFiles";
    public static final String GIT_DIALOG_NO_REMOTE_CHANGES = "git.dialog.noRemoteChanges";

    // 冲突详情相关
    public static final String GIT_DIALOG_CONFLICT_DETAILS_TITLE = "git.dialog.conflictDetails.title";
    public static final String GIT_DIALOG_CONFLICT_FILE = "git.dialog.conflictFile";
    public static final String GIT_DIALOG_CONFLICT_BLOCK = "git.dialog.conflictBlock";
    public static final String GIT_DIALOG_CONFLICT_BLOCK_LINES = "git.dialog.conflictBlock.lines";
    public static final String GIT_DIALOG_CONFLICT_BASE = "git.dialog.conflictBase";
    public static final String GIT_DIALOG_CONFLICT_LOCAL = "git.dialog.conflictLocal";
    public static final String GIT_DIALOG_CONFLICT_REMOTE = "git.dialog.conflictRemote";
    public static final String GIT_DIALOG_NO_CONFLICT_DETAILS = "git.dialog.noConflictDetails";
    public static final String GIT_DIALOG_NO_FILE_CONFLICTS = "git.dialog.noFileConflicts";

    // 操作执行相关
    public static final String GIT_DIALOG_OPERATION_COMPLETED = "git.dialog.operation.completed";
    public static final String GIT_DIALOG_OPERATION_SUCCESS_MESSAGE = "git.dialog.operation.successMessage";
    public static final String GIT_DIALOG_OPERATION_SUCCESS_TITLE = "git.dialog.operation.successTitle";
    public static final String GIT_DIALOG_OPERATION_FAILED = "git.dialog.operation.failed";
    public static final String GIT_DIALOG_OPERATION_FAILED_MESSAGE = "git.dialog.operation.failedMessage";
    public static final String GIT_DIALOG_OPERATION_FAILED_TITLE = "git.dialog.operation.failedTitle";
    public static final String GIT_DIALOG_OPERATION_EXECUTING = "git.dialog.operation.executing";
    public static final String GIT_DIALOG_OPERATION_EXECUTING_PROGRESS = "git.dialog.operation.executingProgress";

    // 各种操作进度消息
    public static final String GIT_DIALOG_PROGRESS_COMMITTING = "git.dialog.progress.committing";
    public static final String GIT_DIALOG_PROGRESS_REMOTE_COMMITS_PULL_FIRST = "git.dialog.progress.remoteCommitsPullFirst";
    public static final String GIT_DIALOG_PROGRESS_COMMIT_DONE_PUSHING = "git.dialog.progress.commitDonePushing";
    public static final String GIT_DIALOG_PROGRESS_FORCE_PUSHING = "git.dialog.progress.forcePushing";
    public static final String GIT_DIALOG_PROGRESS_PULL_FIRST = "git.dialog.progress.pullFirst";
    public static final String GIT_DIALOG_PROGRESS_THEN_PUSH = "git.dialog.progress.thenPush";
    public static final String GIT_DIALOG_PROGRESS_PUSHING = "git.dialog.progress.pushing";
    public static final String GIT_DIALOG_PROGRESS_COMMIT_LOCAL_FIRST = "git.dialog.progress.commitLocalFirst";
    public static final String GIT_DIALOG_PROGRESS_THEN_PULL = "git.dialog.progress.thenPull";
    public static final String GIT_DIALOG_PROGRESS_STASHING = "git.dialog.progress.stashing";
    public static final String GIT_DIALOG_PROGRESS_PULLING_REMOTE = "git.dialog.progress.pullingRemote";
    public static final String GIT_DIALOG_PROGRESS_RESTORING_STASH = "git.dialog.progress.restoringStash";
    public static final String GIT_DIALOG_PROGRESS_FORCE_PULL_DISCARD = "git.dialog.progress.forcePullDiscard";
    public static final String GIT_DIALOG_PROGRESS_PULLING_FROM_REMOTE = "git.dialog.progress.pullingFromRemote";
    public static final String GIT_DIALOG_USER_CANCELLED = "git.dialog.userCancelled";

    // 验证相关
    public static final String GIT_DIALOG_VALIDATION_COMMIT_MESSAGE_EMPTY = "git.dialog.validation.commitMessage.empty";
    public static final String GIT_DIALOG_VALIDATION_COMMIT_MESSAGE_TITLE = "git.dialog.validation.commitMessage.title";

    // ============ GitConflictDetector 国际化 ============
    // 基本检查错误消息
    public static final String GIT_CONFLICT_DETECTOR_CHECK_FAILED = "git.conflictDetector.checkFailed";
    public static final String GIT_CONFLICT_DETECTOR_NO_REMOTE_REPO = "git.conflictDetector.noRemoteRepo";
    public static final String GIT_CONFLICT_DETECTOR_NO_UPSTREAM_BRANCH = "git.conflictDetector.noUpstreamBranch";
    public static final String GIT_CONFLICT_DETECTOR_CANNOT_DETECT_REMOTE_CHANGES = "git.conflictDetector.cannotDetectRemoteChanges";
    public static final String GIT_CONFLICT_DETECTOR_CANNOT_GET_LATEST_REMOTE = "git.conflictDetector.cannotGetLatestRemote";
    public static final String GIT_CONFLICT_DETECTOR_CANNOT_CHECK_REMOTE = "git.conflictDetector.cannotCheckRemote";
    public static final String GIT_CONFLICT_DETECTOR_CANNOT_COUNT_COMMITS = "git.conflictDetector.cannotCountCommits";

    // 远程仓库状态建议
    public static final String GIT_CONFLICT_DETECTOR_REMOTE_REPO_EMPTY = "git.conflictDetector.remoteRepoEmpty";
    public static final String GIT_CONFLICT_DETECTOR_REMOTE_NO_SAME_BRANCH = "git.conflictDetector.remoteNoSameBranch";
    public static final String GIT_CONFLICT_DETECTOR_WAITING_FIRST_PUSH = "git.conflictDetector.waitingFirstPush";
    public static final String GIT_CONFLICT_DETECTOR_REMOTE_HAS_NEW_COMMITS = "git.conflictDetector.remoteHasNewCommits";

    // Init 类型工作区检测
    public static final String GIT_CONFLICT_DETECTOR_INIT_REMOTE_BRANCH_EXISTS = "git.conflictDetector.initRemoteBranchExists";
    public static final String GIT_CONFLICT_DETECTOR_INIT_BACKUP_SUGGESTION = "git.conflictDetector.initBackupSuggestion";
    public static final String GIT_CONFLICT_DETECTOR_INIT_SAFE_FIRST_PUSH = "git.conflictDetector.initSafeFirstPush";
    public static final String GIT_CONFLICT_DETECTOR_INIT_CANNOT_GET_REMOTE_INFO = "git.conflictDetector.initCannotGetRemoteInfo";
    public static final String GIT_CONFLICT_DETECTOR_INIT_CHECK_NETWORK_AUTH = "git.conflictDetector.initCheckNetworkAuth";

    // 文件冲突检查相关
    public static final String GIT_CONFLICT_DETECTOR_CHECK_INIT_CONFLICTS_FAILED = "git.conflictDetector.checkInitConflictsFailed";
    public static final String GIT_CONFLICT_DETECTOR_FILE_CONFLICTS_DETECTED = "git.conflictDetector.fileConflictsDetected";
    public static final String GIT_CONFLICT_DETECTOR_CONFLICT_FILES = "git.conflictDetector.conflictFiles";
    public static final String GIT_CONFLICT_DETECTOR_MORE_FILES_CONFLICT = "git.conflictDetector.moreFilesConflict";
    public static final String GIT_CONFLICT_DETECTOR_GIT_MERGE_SUGGESTION = "git.conflictDetector.gitMergeSuggestion";

    // 建议生成相关
    public static final String GIT_CONFLICT_DETECTOR_UNKNOWN_OPERATION_TYPE = "git.conflictDetector.unknownOperationType";
    public static final String GIT_CONFLICT_DETECTOR_CAN_COMMIT_CHANGES = "git.conflictDetector.canCommitChanges";
    public static final String GIT_CONFLICT_DETECTOR_MODIFIED_FILES_WILL_BE_COMMITTED = "git.conflictDetector.modifiedFilesWillBeCommitted";
    public static final String GIT_CONFLICT_DETECTOR_NO_CHANGES_TO_COMMIT = "git.conflictDetector.noChangesToCommit";
    public static final String GIT_CONFLICT_DETECTOR_ALL_FILES_UP_TO_DATE = "git.conflictDetector.allFilesUpToDate";
    public static final String GIT_CONFLICT_DETECTOR_UNCOMMITTED_CHANGES_CANNOT_PUSH = "git.conflictDetector.uncommittedChangesCannotPush";
    public static final String GIT_CONFLICT_DETECTOR_COMMIT_FIRST_THEN_PUSH = "git.conflictDetector.commitFirstThenPush";

    // Push 建议相关
    public static final String GIT_CONFLICT_DETECTOR_DIVERGED_HISTORY = "git.conflictDetector.divergedHistory";
    public static final String GIT_CONFLICT_DETECTOR_LOCAL_AHEAD_COMMITS = "git.conflictDetector.localAheadCommits";
    public static final String GIT_CONFLICT_DETECTOR_REMOTE_AHEAD_COMMITS = "git.conflictDetector.remoteAheadCommits";
    public static final String GIT_CONFLICT_DETECTOR_PULL_FIRST_OR_FORCE_PUSH = "git.conflictDetector.pullFirstOrForcePush";
    public static final String GIT_CONFLICT_DETECTOR_FORCE_PUSH_OVERWRITE_COMMITS = "git.conflictDetector.forcePushOverwriteCommits";
    public static final String GIT_CONFLICT_DETECTOR_REMOTE_HAS_NEW_COMMITS_WARN = "git.conflictDetector.remoteHasNewCommitsWarn";
    public static final String GIT_CONFLICT_DETECTOR_REMOTE_AHEAD_COMMITS_COUNT = "git.conflictDetector.remoteAheadCommitsCount";
    public static final String GIT_CONFLICT_DETECTOR_PULL_FIRST_THEN_PUSH = "git.conflictDetector.pullFirstThenPush";
    public static final String GIT_CONFLICT_DETECTOR_AVOID_PUSH_CONFLICTS = "git.conflictDetector.avoidPushConflicts";
    public static final String GIT_CONFLICT_DETECTOR_SAFE_PUSH_LOCAL_COMMITS = "git.conflictDetector.safePushLocalCommits";
    public static final String GIT_CONFLICT_DETECTOR_REMOTE_SYNC_AFTER_PUSH = "git.conflictDetector.remoteSyncAfterPush";

    // 首次推送建议相关
    public static final String GIT_CONFLICT_DETECTOR_FIRST_PUSH_OVERWRITE_WARNING = "git.conflictDetector.firstPushOverwriteWarning";
    public static final String GIT_CONFLICT_DETECTOR_FILES_MAY_CONFLICT = "git.conflictDetector.filesMayConflict";
    public static final String GIT_CONFLICT_DETECTOR_USE_FORCE_WITH_LEASE = "git.conflictDetector.useForceWithLease";
    public static final String GIT_CONFLICT_DETECTOR_PULL_REMOTE_FIRST = "git.conflictDetector.pullRemoteFirst";
    public static final String GIT_CONFLICT_DETECTOR_CONFIRM_OVERWRITE_FILES = "git.conflictDetector.confirmOverwriteFiles";
    public static final String GIT_CONFLICT_DETECTOR_REMOTE_EMPTY_SAFE_PUSH = "git.conflictDetector.remoteEmptySafePush";
    public static final String GIT_CONFLICT_DETECTOR_PUSH_COMMITS_TO_REMOTE = "git.conflictDetector.pushCommitsToRemote";
    public static final String GIT_CONFLICT_DETECTOR_AUTO_SET_UPSTREAM = "git.conflictDetector.autoSetUpstream";
    public static final String GIT_CONFLICT_DETECTOR_FIRST_PUSH_DETECTED = "git.conflictDetector.firstPushDetected";
    public static final String GIT_CONFLICT_DETECTOR_PUSH_COMMITS_COUNT = "git.conflictDetector.pushCommitsCount";
    public static final String GIT_CONFLICT_DETECTOR_SET_UPSTREAM_TRACKING = "git.conflictDetector.setUpstreamTracking";

    // Pull 建议相关
    public static final String GIT_CONFLICT_DETECTOR_CANNOT_PULL_FILE_CONFLICTS = "git.conflictDetector.cannotPullFileConflicts";
    public static final String GIT_CONFLICT_DETECTOR_MANUAL_HANDLE_CONFLICTS = "git.conflictDetector.manualHandleConflicts";
    public static final String GIT_CONFLICT_DETECTOR_BACKUP_LOCAL_FILES = "git.conflictDetector.backupLocalFiles";
    public static final String GIT_CONFLICT_DETECTOR_GIT_FETCH_ORIGIN = "git.conflictDetector.gitFetchOrigin";
    public static final String GIT_CONFLICT_DETECTOR_MANUAL_MERGE_FILES = "git.conflictDetector.manualMergeFiles";
    public static final String GIT_CONFLICT_DETECTOR_CREATE_MERGE_COMMIT = "git.conflictDetector.createMergeCommit";
    public static final String GIT_CONFLICT_DETECTOR_CANNOT_PULL_NO_UPSTREAM = "git.conflictDetector.cannotPullNoUpstream";
    public static final String GIT_CONFLICT_DETECTOR_CONFIG_REMOTE_FIRST = "git.conflictDetector.configRemoteFirst";
    public static final String GIT_CONFLICT_DETECTOR_FIRST_PUSH_ESTABLISH_TRACKING = "git.conflictDetector.firstPushEstablishTracking";
    public static final String GIT_CONFLICT_DETECTOR_REMOTE_REPO_STATUS_EMPTY = "git.conflictDetector.remoteRepoStatusEmpty";
    public static final String GIT_CONFLICT_DETECTOR_CAN_TRY_PULL_NO_CONTENT = "git.conflictDetector.canTryPullNoContent";
    public static final String GIT_CONFLICT_DETECTOR_PUSH_LOCAL_CONTENT_FIRST = "git.conflictDetector.pushLocalContentFirst";
    public static final String GIT_CONFLICT_DETECTOR_UNCOMMITTED_PULL_CONFLICTS = "git.conflictDetector.uncommittedPullConflicts";
    public static final String GIT_CONFLICT_DETECTOR_COMMIT_OR_STASH_FIRST = "git.conflictDetector.commitOrStashFirst";
    public static final String GIT_CONFLICT_DETECTOR_FORCE_PULL_LOSE_CHANGES = "git.conflictDetector.forcePullLoseChanges";
    public static final String GIT_CONFLICT_DETECTOR_LOCAL_IS_UP_TO_DATE = "git.conflictDetector.localIsUpToDate";
    public static final String GIT_CONFLICT_DETECTOR_SAFE_PULL_REMOTE_COMMITS = "git.conflictDetector.safePullRemoteCommits";

    // 冲突检测相关
    public static final String GIT_CONFLICT_DETECTOR_NO_COMMON_HISTORY = "git.conflictDetector.noCommonHistory";
    public static final String GIT_CONFLICT_DETECTOR_ACTUAL_FILE_CONFLICTS = "git.conflictDetector.actualFileConflicts";
    public static final String GIT_CONFLICT_DETECTOR_CONFLICT_FILES_LIST = "git.conflictDetector.conflictFilesList";
    public static final String GIT_CONFLICT_DETECTOR_MORE_CONFLICT_FILES = "git.conflictDetector.moreConflictFiles";
    public static final String GIT_CONFLICT_DETECTOR_NON_OVERLAPPING_CHANGES = "git.conflictDetector.nonOverlappingChanges";
    public static final String GIT_CONFLICT_DETECTOR_ONLY_NEW_FILES_SAFE = "git.conflictDetector.onlyNewFilesSafe";

    // 网络代理设置
    public static final String SETTINGS_PROXY_TITLE = "settings.proxy.title";
    public static final String SETTINGS_PROXY_ENABLED_TOOLTIP = "settings.proxy.enabled.tooltip";
    public static final String SETTINGS_PROXY_ENABLED_CHECKBOX = "settings.proxy.enabled.checkbox";
    public static final String SETTINGS_PROXY_TYPE = "settings.proxy.type";
    public static final String SETTINGS_PROXY_TYPE_TOOLTIP = "settings.proxy.type.tooltip";
    public static final String SETTINGS_PROXY_TYPE_HTTP = "settings.proxy.type.http";
    public static final String SETTINGS_PROXY_TYPE_SOCKS = "settings.proxy.type.socks";
    public static final String SETTINGS_PROXY_HOST = "settings.proxy.host";
    public static final String SETTINGS_PROXY_HOST_TOOLTIP = "settings.proxy.host.tooltip";
    public static final String SETTINGS_PROXY_PORT = "settings.proxy.port";
    public static final String SETTINGS_PROXY_PORT_TOOLTIP = "settings.proxy.port.tooltip";
    public static final String SETTINGS_PROXY_USERNAME = "settings.proxy.username";
    public static final String SETTINGS_PROXY_USERNAME_TOOLTIP = "settings.proxy.username.tooltip";
    public static final String SETTINGS_PROXY_PASSWORD = "settings.proxy.password";
    public static final String SETTINGS_PROXY_PASSWORD_TOOLTIP = "settings.proxy.password.tooltip";
    public static final String SETTINGS_PROXY_SSL_VERIFICATION_TOOLTIP = "settings.proxy.ssl.verification.tooltip";
    public static final String SETTINGS_PROXY_SSL_VERIFICATION_CHECKBOX = "settings.proxy.ssl.verification.checkbox";

    // ============ 工具箱相关 ============
    public static final String TOOLBOX_ENCODER = "toolbox.encoder";
    public static final String TOOLBOX_ENCODER_TITLE = "toolbox.encoder.title";
    public static final String TOOLBOX_ENCODER_INPUT = "toolbox.encoder.input";
    public static final String TOOLBOX_ENCODER_OUTPUT = "toolbox.encoder.output";
    public static final String TOOLBOX_ENCODER_ENCODE = "toolbox.encoder.encode";
    public static final String TOOLBOX_ENCODER_DECODE = "toolbox.encoder.decode";

    public static final String TOOLBOX_CRYPTO = "toolbox.crypto";
    public static final String TOOLBOX_CRYPTO_ALGORITHM = "toolbox.crypto.algorithm";
    public static final String TOOLBOX_CRYPTO_MODE = "toolbox.crypto.mode";
    public static final String TOOLBOX_CRYPTO_INPUT = "toolbox.crypto.input";
    public static final String TOOLBOX_CRYPTO_OUTPUT = "toolbox.crypto.output";
    public static final String TOOLBOX_CRYPTO_KEY = "toolbox.crypto.key";
    public static final String TOOLBOX_CRYPTO_IV = "toolbox.crypto.iv";
    public static final String TOOLBOX_CRYPTO_ENCRYPT = "toolbox.crypto.encrypt";
    public static final String TOOLBOX_CRYPTO_DECRYPT = "toolbox.crypto.decrypt";
    public static final String TOOLBOX_CRYPTO_GENERATE_KEY = "toolbox.crypto.generate_key";
    public static final String TOOLBOX_CRYPTO_GENERATE_IV = "toolbox.crypto.generate_iv";
    public static final String TOOLBOX_CRYPTO_BASE64URL = "toolbox.crypto.base64url";
    public static final String TOOLBOX_CRYPTO_BASE64URL_TOOLTIP = "toolbox.crypto.base64url.tooltip";
    public static final String TOOLBOX_CRYPTO_ERROR_KEY_REQUIRED = "toolbox.crypto.error.key_required";
    public static final String TOOLBOX_CRYPTO_ERROR_KEY_LENGTH = "toolbox.crypto.error.key_length";
    public static final String TOOLBOX_CRYPTO_ERROR_IV_REQUIRED = "toolbox.crypto.error.iv_required";
    public static final String TOOLBOX_CRYPTO_ERROR_IV_LENGTH = "toolbox.crypto.error.iv_length";
    public static final String TOOLBOX_CRYPTO_ERROR_INPUT_EMPTY = "toolbox.crypto.error.input_empty";
    public static final String TOOLBOX_CRYPTO_SUCCESS_ENCRYPTED = "toolbox.crypto.success.encrypted";
    public static final String TOOLBOX_CRYPTO_SUCCESS_DECRYPTED = "toolbox.crypto.success.decrypted";
    public static final String TOOLBOX_CRYPTO_KEY_PLACEHOLDER_AES128 = "toolbox.crypto.key.placeholder.aes128";
    public static final String TOOLBOX_CRYPTO_KEY_PLACEHOLDER_AES256 = "toolbox.crypto.key.placeholder.aes256";
    public static final String TOOLBOX_CRYPTO_KEY_PLACEHOLDER_DES = "toolbox.crypto.key.placeholder.des";
    public static final String TOOLBOX_CRYPTO_IV_PLACEHOLDER = "toolbox.crypto.iv.placeholder";

    public static final String TOOLBOX_HASH = "toolbox.hash";
    public static final String TOOLBOX_HASH_INPUT = "toolbox.hash.input";
    public static final String TOOLBOX_HASH_OUTPUT = "toolbox.hash.output";
    public static final String TOOLBOX_HASH_CALCULATE_ALL = "toolbox.hash.calculate_all";
    public static final String TOOLBOX_HASH_CALCULATE_ALL_TOOLTIP = "toolbox.hash.calculate_all.tooltip";
    public static final String TOOLBOX_HASH_UPPERCASE = "toolbox.hash.uppercase";
    public static final String TOOLBOX_HASH_UPPERCASE_TOOLTIP = "toolbox.hash.uppercase.tooltip";

    // ============ JSON 工具相关 ============
    public static final String TOOLBOX_JSON = "toolbox.json";
    public static final String TOOLBOX_JSON_FORMAT = "toolbox.json.format";
    public static final String TOOLBOX_JSON_COMPRESS = "toolbox.json.compress";
    public static final String TOOLBOX_JSON_VALIDATE = "toolbox.json.validate";
    public static final String TOOLBOX_JSON_ESCAPE = "toolbox.json.escape";
    public static final String TOOLBOX_JSON_UNESCAPE = "toolbox.json.unescape";
    public static final String TOOLBOX_JSON_SORT_KEYS = "toolbox.json.sort_keys";
    public static final String TOOLBOX_JSON_SWAP = "toolbox.json.swap";
    public static final String TOOLBOX_JSON_PASTE = "toolbox.json.paste";
    public static final String TOOLBOX_JSON_INPUT = "toolbox.json.input";
    public static final String TOOLBOX_JSON_OUTPUT = "toolbox.json.output";
    public static final String TOOLBOX_JSON_ERROR = "toolbox.json.error";

    // JSON 工具状态消息
    public static final String TOOLBOX_JSON_STATUS_EMPTY = "toolbox.json.status.empty";
    public static final String TOOLBOX_JSON_STATUS_FORMATTED = "toolbox.json.status.formatted";
    public static final String TOOLBOX_JSON_STATUS_COMPRESSED = "toolbox.json.status.compressed";
    public static final String TOOLBOX_JSON_STATUS_VALIDATED = "toolbox.json.status.validated";
    public static final String TOOLBOX_JSON_STATUS_INVALID = "toolbox.json.status.invalid";
    public static final String TOOLBOX_JSON_STATUS_ESCAPED = "toolbox.json.status.escaped";
    public static final String TOOLBOX_JSON_STATUS_UNESCAPED = "toolbox.json.status.unescaped";
    public static final String TOOLBOX_JSON_STATUS_SORTED = "toolbox.json.status.sorted";
    public static final String TOOLBOX_JSON_STATUS_SWAPPED = "toolbox.json.status.swapped";
    public static final String TOOLBOX_JSON_STATUS_COPIED = "toolbox.json.status.copied";
    public static final String TOOLBOX_JSON_STATUS_PASTED = "toolbox.json.status.pasted";
    public static final String TOOLBOX_JSON_STATUS_CLEARED = "toolbox.json.status.cleared";
    public static final String TOOLBOX_JSON_STATUS_OUTPUT_EMPTY = "toolbox.json.status.output_empty";
    public static final String TOOLBOX_JSON_STATUS_NOT_OBJECT = "toolbox.json.status.not_object";

    // JSON 验证相关
    public static final String TOOLBOX_JSON_VALIDATION_EMPTY = "toolbox.json.validation.empty";
    public static final String TOOLBOX_JSON_VALIDATION_VALID = "toolbox.json.validation.valid";
    public static final String TOOLBOX_JSON_VALIDATION_TYPE = "toolbox.json.validation.type";
    public static final String TOOLBOX_JSON_VALIDATION_CHARACTERS = "toolbox.json.validation.characters";
    public static final String TOOLBOX_JSON_VALIDATION_LINES = "toolbox.json.validation.lines";
    public static final String TOOLBOX_JSON_VALIDATION_KEYS = "toolbox.json.validation.keys";
    public static final String TOOLBOX_JSON_VALIDATION_ARRAY_LENGTH = "toolbox.json.validation.array_length";

    // JSON 工具提示
    public static final String TOOLBOX_JSON_TOOLTIP_FORMAT = "toolbox.json.tooltip.format";
    public static final String TOOLBOX_JSON_TOOLTIP_COMPRESS = "toolbox.json.tooltip.compress";
    public static final String TOOLBOX_JSON_TOOLTIP_VALIDATE = "toolbox.json.tooltip.validate";
    public static final String TOOLBOX_JSON_TOOLTIP_ESCAPE = "toolbox.json.tooltip.escape";
    public static final String TOOLBOX_JSON_TOOLTIP_UNESCAPE = "toolbox.json.tooltip.unescape";
    public static final String TOOLBOX_JSON_TOOLTIP_SORT = "toolbox.json.tooltip.sort";
    public static final String TOOLBOX_JSON_TOOLTIP_COPY = "toolbox.json.tooltip.copy";
    public static final String TOOLBOX_JSON_TOOLTIP_PASTE = "toolbox.json.tooltip.paste";
    public static final String TOOLBOX_JSON_TOOLTIP_CLEAR = "toolbox.json.tooltip.clear";
    public static final String TOOLBOX_JSON_TOOLTIP_SWAP = "toolbox.json.tooltip.swap";

    // ============ 时间戳工具相关 ============
    public static final String TOOLBOX_TIMESTAMP = "toolbox.timestamp";
    public static final String TOOLBOX_TIMESTAMP_CURRENT = "toolbox.timestamp.current";
    public static final String TOOLBOX_TIMESTAMP_INPUT = "toolbox.timestamp.input";
    public static final String TOOLBOX_TIMESTAMP_OUTPUT = "toolbox.timestamp.output";
    public static final String TOOLBOX_TIMESTAMP_MILLISECONDS = "toolbox.timestamp.milliseconds";
    public static final String TOOLBOX_TIMESTAMP_SECONDS = "toolbox.timestamp.seconds";
    public static final String TOOLBOX_TIMESTAMP_TO_DATE = "toolbox.timestamp.toDate";
    public static final String TOOLBOX_TIMESTAMP_DATE_TO_TIMESTAMP = "toolbox.timestamp.dateToTimestamp";
    public static final String TOOLBOX_TIMESTAMP_DATE_INPUT = "toolbox.timestamp.dateInput";
    public static final String TOOLBOX_TIMESTAMP_DATE_FORMAT_HINT = "toolbox.timestamp.dateFormatHint";
    public static final String TOOLBOX_TIMESTAMP_NOW_BUTTON = "toolbox.timestamp.nowButton";
    public static final String TOOLBOX_TIMESTAMP_NOW_TOOLTIP = "toolbox.timestamp.nowTooltip";
    public static final String TOOLBOX_TIMESTAMP_CLEAR_BUTTON = "toolbox.timestamp.clearButton";
    public static final String TOOLBOX_TIMESTAMP_FORMATTED_DATES = "toolbox.timestamp.formattedDates";
    public static final String TOOLBOX_TIMESTAMP_STANDARD = "toolbox.timestamp.standard";
    public static final String TOOLBOX_TIMESTAMP_ISO8601 = "toolbox.timestamp.iso8601";
    public static final String TOOLBOX_TIMESTAMP_HTTP_DATE = "toolbox.timestamp.httpDate";
    public static final String TOOLBOX_TIMESTAMP_TIMESTAMPS = "toolbox.timestamp.timestamps";
    public static final String TOOLBOX_TIMESTAMP_ADDITIONAL_INFO = "toolbox.timestamp.additionalInfo";
    public static final String TOOLBOX_TIMESTAMP_DAY_OF_WEEK = "toolbox.timestamp.dayOfWeek";
    public static final String TOOLBOX_TIMESTAMP_WEEK_OF_YEAR = "toolbox.timestamp.weekOfYear";
    public static final String TOOLBOX_TIMESTAMP_ERROR = "toolbox.timestamp.error";
    public static final String TOOLBOX_TIMESTAMP_INVALID_DATE_FORMAT = "toolbox.timestamp.invalidDateFormat";
    public static final String TOOLBOX_TIMESTAMP_EXPECTED_FORMAT = "toolbox.timestamp.expectedFormat";
    public static final String TOOLBOX_TIMESTAMP_EXAMPLE = "toolbox.timestamp.example";
    public static final String TOOLBOX_TIMESTAMP_INPUT_DATE = "toolbox.timestamp.inputDate";

    public static final String TOOLBOX_UUID = "toolbox.uuid";
    public static final String TOOLBOX_UUID_GENERATE = "toolbox.uuid.generate";
    public static final String TOOLBOX_UUID_COUNT = "toolbox.uuid.count";
    public static final String TOOLBOX_UUID_FORMAT = "toolbox.uuid.format";
    public static final String TOOLBOX_UUID_UPPERCASE = "toolbox.uuid.uppercase";
    public static final String TOOLBOX_UUID_WITH_HYPHENS = "toolbox.uuid.with_hyphens";
    public static final String TOOLBOX_UUID_WITHOUT_HYPHENS = "toolbox.uuid.without_hyphens";
    public static final String TOOLBOX_UUID_VERSION_INFO = "toolbox.uuid.version_info";
    public static final String TOOLBOX_UUID_GENERATED = "toolbox.uuid.generated";
    public static final String TOOLBOX_UUID_BATCH_GENERATE = "toolbox.uuid.batch_generate";
    public static final String TOOLBOX_UUID_VERSION = "toolbox.uuid.version";
    public static final String TOOLBOX_UUID_SEPARATOR = "toolbox.uuid.separator";
    public static final String TOOLBOX_UUID_SEPARATOR_NEWLINE = "toolbox.uuid.separator_newline";
    public static final String TOOLBOX_UUID_SEPARATOR_COMMA = "toolbox.uuid.separator_comma";
    public static final String TOOLBOX_UUID_SEPARATOR_SPACE = "toolbox.uuid.separator_space";
    public static final String TOOLBOX_UUID_SEPARATOR_SEMICOLON = "toolbox.uuid.separator_semicolon";
    public static final String TOOLBOX_UUID_COPY_ONE = "toolbox.uuid.copy_one";
    public static final String TOOLBOX_UUID_EXPORT = "toolbox.uuid.export";
    public static final String TOOLBOX_UUID_EXPORT_SUCCESS = "toolbox.uuid.export_success";
    public static final String TOOLBOX_UUID_EXPORT_FAILED = "toolbox.uuid.export_failed";
    public static final String TOOLBOX_UUID_EMPTY = "toolbox.uuid.empty";
    public static final String TOOLBOX_UUID_PARSE = "toolbox.uuid.parse";
    public static final String TOOLBOX_UUID_INPUT = "toolbox.uuid.input";
    public static final String TOOLBOX_UUID_PARSE_EMPTY = "toolbox.uuid.parse_empty";
    public static final String TOOLBOX_UUID_PARSE_INVALID = "toolbox.uuid.parse_invalid";
    public static final String TOOLBOX_UUID_PARSE_RESULT = "toolbox.uuid.parse_result";
    public static final String TOOLBOX_UUID_STANDARD_FORMAT = "toolbox.uuid.standard_format";
    public static final String TOOLBOX_UUID_VARIANT = "toolbox.uuid.variant";
    public static final String TOOLBOX_UUID_TIMESTAMP = "toolbox.uuid.timestamp";

    public static final String TOOLBOX_UUID_NAMESPACE = "toolbox.uuid.namespace";
    public static final String TOOLBOX_UUID_NAMESPACE_CUSTOM = "toolbox.uuid.namespace_custom";
    public static final String TOOLBOX_UUID_NAMESPACE_HINT = "toolbox.uuid.namespace_hint";
    public static final String TOOLBOX_UUID_NAME = "toolbox.uuid.name";
    public static final String TOOLBOX_UUID_NAME_BASED = "toolbox.uuid.name_based";
    public static final String TOOLBOX_UUID_NAME_BASED_DESC = "toolbox.uuid.name_based_desc";

    public static final String TOOLBOX_DIFF = "toolbox.diff";
    public static final String TOOLBOX_DIFF_ORIGINAL = "toolbox.diff.original";
    public static final String TOOLBOX_DIFF_MODIFIED = "toolbox.diff.modified";
    public static final String TOOLBOX_DIFF_COMPARE = "toolbox.diff.compare";
    public static final String TOOLBOX_DIFF_RESULT = "toolbox.diff.result";

    public static final String TOOLBOX_CRON = "toolbox.cron";
    public static final String TOOLBOX_CRON_EXPRESSION = "toolbox.cron.expression";
    public static final String TOOLBOX_CRON_PARSE = "toolbox.cron.parse";
    public static final String TOOLBOX_CRON_GENERATE = "toolbox.cron.generate";
    public static final String TOOLBOX_CRON_DESCRIPTION = "toolbox.cron.description";
    public static final String TOOLBOX_CRON_NEXT_EXECUTIONS = "toolbox.cron.next_executions";
    public static final String TOOLBOX_CRON_TAB_PARSE = "toolbox.cron.tab.parse";
    public static final String TOOLBOX_CRON_TAB_GENERATE = "toolbox.cron.tab.generate";
    public static final String TOOLBOX_CRON_PLACEHOLDER = "toolbox.cron.placeholder";
    public static final String TOOLBOX_CRON_EXECUTION_TIME = "toolbox.cron.execution_time";
    public static final String TOOLBOX_CRON_FORMAT = "toolbox.cron.format";
    public static final String TOOLBOX_CRON_SECOND = "toolbox.cron.second";
    public static final String TOOLBOX_CRON_MINUTE = "toolbox.cron.minute";
    public static final String TOOLBOX_CRON_HOUR = "toolbox.cron.hour";
    public static final String TOOLBOX_CRON_DAY = "toolbox.cron.day";
    public static final String TOOLBOX_CRON_MONTH = "toolbox.cron.month";
    public static final String TOOLBOX_CRON_WEEK = "toolbox.cron.week";
    public static final String TOOLBOX_CRON_YEAR = "toolbox.cron.year";
    public static final String TOOLBOX_CRON_YEAR_OPTIONAL = "toolbox.cron.year.optional";
    public static final String TOOLBOX_CRON_YEAR_PLACEHOLDER = "toolbox.cron.year.placeholder";
    public static final String TOOLBOX_CRON_GENERATED = "toolbox.cron.generated";
    public static final String TOOLBOX_CRON_QUICK_PRESETS = "toolbox.cron.quick_presets";
    public static final String TOOLBOX_CRON_COMMON_PRESETS = "toolbox.cron.common_presets";
    public static final String TOOLBOX_CRON_SPECIAL_CHARS = "toolbox.cron.special_chars";
    public static final String TOOLBOX_CRON_COMMON_EXPRESSIONS = "toolbox.cron.common_expressions";
    public static final String TOOLBOX_CRON_PRESET_EVERY_SECOND = "toolbox.cron.preset.every_second";
    public static final String TOOLBOX_CRON_PRESET_EVERY_MINUTE = "toolbox.cron.preset.every_minute";
    public static final String TOOLBOX_CRON_PRESET_EVERY_5MIN = "toolbox.cron.preset.every_5min";
    public static final String TOOLBOX_CRON_PRESET_EVERY_15MIN = "toolbox.cron.preset.every_15min";
    public static final String TOOLBOX_CRON_PRESET_EVERY_30MIN = "toolbox.cron.preset.every_30min";
    public static final String TOOLBOX_CRON_PRESET_EVERY_HOUR = "toolbox.cron.preset.every_hour";
    public static final String TOOLBOX_CRON_PRESET_EVERY_2HOUR = "toolbox.cron.preset.every_2hour";
    public static final String TOOLBOX_CRON_PRESET_DAILY_NOON = "toolbox.cron.preset.daily_noon";
    public static final String TOOLBOX_CRON_PRESET_DAILY_MIDNIGHT = "toolbox.cron.preset.daily_midnight";
    public static final String TOOLBOX_CRON_PRESET_MONDAY_9AM = "toolbox.cron.preset.monday_9am";
    public static final String TOOLBOX_CRON_PRESET_WEEKDAY_9AM = "toolbox.cron.preset.weekday_9am";
    public static final String TOOLBOX_CRON_PRESET_FIRST_DAY_MONTH = "toolbox.cron.preset.first_day_month";
    public static final String TOOLBOX_CRON_PRESET_LAST_DAY_MONTH = "toolbox.cron.preset.last_day_month";
    public static final String TOOLBOX_CRON_ERROR_EMPTY = "toolbox.cron.error.empty";
    public static final String TOOLBOX_CRON_ERROR_INVALID = "toolbox.cron.error.invalid";
    public static final String TOOLBOX_CRON_ERROR_PARSE = "toolbox.cron.error.parse";
    public static final String TOOLBOX_CRON_ANALYSIS = "toolbox.cron.analysis";
    public static final String TOOLBOX_CRON_FIELDS = "toolbox.cron.fields";
    public static final String TOOLBOX_CRON_UNABLE_CALCULATE = "toolbox.cron.unable_calculate";

    // ============ 客户端证书相关 ============
    public static final String CERT_TITLE = "cert.title";
    public static final String CERT_ADD = "cert.add";
    public static final String CERT_EDIT = "cert.edit";
    public static final String CERT_DELETE = "cert.delete";
    public static final String CERT_DELETE_CONFIRM = "cert.delete.confirm";
    public static final String CERT_NAME = "cert.name";
    public static final String CERT_HOST = "cert.host";
    public static final String CERT_PORT = "cert.port";
    public static final String CERT_CERT_TYPE = "cert.cert_type";
    public static final String CERT_CERT_PATH = "cert.cert_path";
    public static final String CERT_KEY_PATH = "cert.key_path";
    public static final String CERT_PASSWORD = "cert.password";
    public static final String CERT_ENABLED = "cert.enabled";
    public static final String CERT_SELECT_FILE = "cert.select_file";
    public static final String CERT_TYPE_PFX = "cert.type.pfx";
    public static final String CERT_TYPE_PEM = "cert.type.pem";
    public static final String CERT_PORT_ALL = "cert.port.all";
    public static final String CERT_PORT_PLACEHOLDER = "cert.port.placeholder";
    public static final String CERT_HOST_PLACEHOLDER = "cert.host.placeholder";
    public static final String CERT_NAME_PLACEHOLDER = "cert.name.placeholder";
    public static final String CERT_CERT_PATH_PLACEHOLDER = "cert.cert_path.placeholder";
    public static final String CERT_KEY_PATH_PLACEHOLDER = "cert.key_path.placeholder";
    public static final String CERT_VALIDATION_HOST_REQUIRED = "cert.validation.host_required";
    public static final String CERT_VALIDATION_CERT_REQUIRED = "cert.validation.cert_required";
    public static final String CERT_VALIDATION_KEY_REQUIRED = "cert.validation.key_required";
    public static final String CERT_VALIDATION_FILE_NOT_FOUND = "cert.validation.file_not_found";
    public static final String CERT_SAVE = "cert.save";
    public static final String CERT_CANCEL = "cert.cancel";
    public static final String CERT_HELP = "cert.help";
    public static final String CERT_HELP_TITLE = "cert.help.title";
    public static final String CERT_HELP_CONTENT = "cert.help.content";
    public static final String CERT_CLOSE = "cert.close";
    public static final String CERT_ERROR = "cert.error";
    public static final String CERT_DESCRIPTION = "cert.description";
    public static final String CERT_LIST_TITLE = "cert.list.title";
    public static final String CERT_ADD_SUCCESS = "cert.add.success";
    public static final String CERT_EDIT_SUCCESS = "cert.edit.success";
    public static final String CERT_DELETE_SUCCESS = "cert.delete.success";
    public static final String CERT_STATUS_UPDATED = "cert.status.updated";

    // Client Certificate Console Logs
    public static final String CERT_CONSOLE_MATCHED = "cert.console.matched";
    public static final String CERT_CONSOLE_LOADED = "cert.console.loaded";
    public static final String CERT_CONSOLE_LOAD_FAILED = "cert.console.load_failed";
    public static final String CERT_CONSOLE_VALIDATION_FAILED = "cert.console.validation_failed";
    public static final String CERT_CONSOLE_FILE_NOT_FOUND = "cert.console.file_not_found";

}