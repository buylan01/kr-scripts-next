
# Switch

## 示例
```xml
<?xml version="1.0" encoding="UTF-8" ?>
<page>
    <switch>
        <title>测试开关</title>
        <desc>测试开关功能</desc>
        <get>getprop test.switch.aaa</get>
        <set>setprop test.switch.aaa "$state"</set>
    </switch>
</page>
```

## Switch 属性

- 公共属性（Action、Switch、Picker共有）

<!-- @include: ./common-props.md -->

> `id` 属性建议配合 `auto-off`、`auto-finish`、`logo` 使用

> `logo`和`icon`除了支持assets文件路径，也支持磁盘文件路径

## switch > get
- 自定义一段脚本，输出 `1` 或 `0` 来确定开关当前状态

## switch > set
- 自定义用户点击开关后要执行的代码，开关状态会以`$state`参数传入脚本

---

>由于在xml中写大量的shell代码非常不方便，也不美观，建议参考 [`脚本使用`](./Script.md) 中的说明，将段落较长的脚本代码，写在单独的文件中。
