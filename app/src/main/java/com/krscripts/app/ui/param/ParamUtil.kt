package com.krscripts.app.ui.param

import android.content.Context
import android.os.Parcelable
import com.krscripts.app.executor.ScriptEnvironment
import com.krscripts.app.model.ActionParamInfo
import com.krscripts.app.model.NodeInfoBase
import com.krscripts.app.model.SelectItem
import kotlinx.parcelize.Parcelize

object ParamUtil {
    fun getParamOptions(
        context: Context,
        actionParamInfo: ActionParamInfo,
        nodeInfoBase: NodeInfoBase?
    ): ArrayList<SelectItem>? {
        val options = ArrayList<SelectItem>()
        var shellResult = ""
        if (!actionParamInfo.optionsSh.isEmpty()) {
            shellResult = ScriptEnvironment.execute(context, actionParamInfo.optionsSh, nodeInfoBase)
        }

        if (!(shellResult == "error" || shellResult == "null" || shellResult.isEmpty())) {
            for (item in shellResult.split("\n")) {
                if (item.contains('|')) {
                    val data = item.split('|')
                    val item = SelectItem(
                        title = data[1],
                        value = data[0]
                    )
                    options.add(item)
                } else {
                    val item = SelectItem(
                        title = item,
                        value = item
                    )
                    options.add(item)
                }
            }
        } else if (actionParamInfo.options != null) {
            for (option in actionParamInfo.options!!) {
                options.add(option)
            }
        } else {
            return null
        }

        return options
    }
}

@Parcelize
data class ParamsResult(val values: HashMap<String, String>) : Parcelable