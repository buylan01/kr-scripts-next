# Picker
- `picker`即加强版的`switch`
- `picker`和`switch`一样，通过`get`读取当前状态，通过`set`保存状态
- 其它配置项也和`switch`一致
- `picker`需要自己定义选项(`option`)

### 属性

- 公共属性（Action、Switch、Picker共有）

<!-- @include: ./common-props.md -->

- 特有属性

| 属性 | 作用 | 有效值 | 示例 |
| - | - | - | :- |
| multiple | 是否允许多选(设置了options或type=app时可用) | `true` `false` | `true` |
| separator | 多选模式下多个值的分隔符，默认为换行符 | 任意字符 | `,` |


> `id` 属性建议配合 `auto-off`、`auto-finish`、`logo` 使用

> `logo`和`icon`除了支持assets文件路径，也支持磁盘文件路径

```xml
<picker>
    <title>单选列表</title>
    <desc>测试单选列表</desc>
    <options>
        <option value="a1">选项1</option>
        <option value="a2">选项1</option>
    </options>
    <get>getprop xxx.xxx.xxx</get>
    <set>setprop xxx.xxx.xxx $state</set>
</picker>
```

- **动态选项**
- picker也允许使用`options-sh`属性来设置输出下拉选项的脚本
- 用法和action的param一样，如：

```xml
<picker options-sh="echo 'a|选项A'; echo 'b|选项B'">
    <title>测试单选界面</title>
    <desc>测试单选界面</desc>
    <get>getprop xxx.xxx.xxx3</get>
    <set>setprop xxx.xxx.xxx3 "$state"</set>
</picker>
```

- **多选模式**
- 在picker节点上增加`multiple="true"`属性来标识允许多选
- 例如：

    ```xml
    <picker options-sh="echo 'a|选项A'; echo 'b|选项B'" value-sh="echo 'a'; echo 'b';">
        <title>测试单选界面</title>
        <get>getprop xxx.xxx.xxx4</get>
        <set>setprop xxx.xxx.xxx4 "$state"</set>
    </picker>
    ```

- 默认设置下，多选列表的各个值用换行分隔，得到的参数可能是这样的
    ```sh
    value="
    wifi
    airplane
    "
    ```
- 可有时候，你希望得到的值是 `value="wifi,airplane"` 这样的？
- 其实你可以通过`separator`属性自定义分隔符，例如：
    ```xml
    <picker multiple="multiple" separator=",">
        <title>隐藏状态栏图标</title>
        <desc>设置隐藏的状态栏图标</desc>
        <options>
            <option value="mobile">手机信号</option>
            <option value="wifi">WIFI</option>
            <option value="airplane">飞行模式</option>
        </options>
        <get>
            settings get secure icon_blacklist
        </get>
        <set>
            settings put secure icon_blacklist "$state"
        </set>
    </picker>
    ```


#

---

> 相关说明

- 由于在xml中写大量的shell代码非常不方便，也不美观，
- 建议参考 [`脚本使用`](./Script.md) 中的说明，
- 将段落较长的脚本代码，写在单独的文件中。
