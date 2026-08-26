#!/system/bin/sh

# 脚本路径，$1 为传入的第一个参数
script_path="$1"

# 全局变量，大部分可运行脚本的地方都能获取到
# 删除未被使用的变量不会影响脚本运行
# 非必要请勿在此文件编写耗时操作
# {} 中的内容会被框架动态替换，但里面只能写下面有的
export EXECUTOR_PATH="{EXECUTOR_PATH}"
export START_DIR="{START_DIR}"
export TEMP_DIR="{TEMP_DIR}"
export ANDROID_UID="{ANDROID_UID}"
export ANDROID_SDK="{ANDROID_SDK}"
export SDCARD_PATH="{SDCARD_PATH}"
export BUSYBOX="{BUSYBOX}"
export PACKAGE_NAME="{PACKAGE_NAME}"
export PACKAGE_VERSION_NAME="{PACKAGE_VERSION_NAME}"
export PACKAGE_VERSION_CODE="{PACKAGE_VERSION_CODE}"
export APP_USER_ID="{APP_USER_ID}"
export ROOT_PERMISSION="{ROOT_PERMISSION}"
export TOOLKIT="{TOOLKIT}"
export TMPDIR="$TEMP_DIR"
export PREF_PATH="$START_DIR/pref"

# 添加工具目录到环境，工具目录的可执行文件优先
if [ ! "$TOOLKIT" = "" ]; then
    PATH="$TOOLKIT:$PATH"
fi

# 跳转到起始目录 (如果有)
if [ "$START_DIR" != "" ] && [ -d "$START_DIR" ]; then
    cd "$START_DIR" || exit 1
fi

# 运行脚本
if [ -f "$script_path" ]; then
    chmod 755 "$script_path"
    # shellcheck disable=SC1090
    . "$script_path"
else
    echo "${script_path} 已丢失" 1>&2
fi
