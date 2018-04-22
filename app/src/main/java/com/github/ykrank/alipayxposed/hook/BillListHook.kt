package com.github.ykrank.alipayxposed.hook

import android.widget.ListView
import com.github.ykrank.alipayxposed.bridge.BillH5ContentValues
import com.github.ykrank.androidtools.extension.toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.ref.WeakReference

object BillListHook {

    //libandroid-phone-wallet-billlist.so
    const val Cls_BillListAdapter = "com.alipay.mobile.bill.list.ui.adapter.BillListAdapter"
    const val Cls_BillListActivity = "com.alipay.mobile.bill.list.ui.BillListActivity" //实际上是BillListActivity_

    fun hookBillList(classLoader: ClassLoader) {
        if (XposedHelpers.findClassIfExists(Cls_BillListActivity, classLoader) == null) {
            XposedBridge.log("Could not find $Cls_BillListActivity")
            return
        }
        //adapter addAll
        XposedHelpers.findAndHookMethod(Cls_BillListAdapter, classLoader, "a",
                List::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
//                XposedBridge.log(Exception("BillListAdapter"))
//                XposedBridge.log("AddAll:${param.args[0]}")
                HookedApp.billListAdapter = WeakReference(param.thisObject)
                HookedApp.billSingleListItems.addAll(param.args[0] as Collection<Any>)
                try {
                    //ListView
                    val listView = XposedHelpers.getObjectField(param.thisObject, "c") as ListView
                    checkNextBill(listView)
                } catch (e: Exception) {
                    XposedBridge.log(e)
                    HookedApp.app?.toast("Parse BillListAdapter error")
                }
            }
        })
        //onResume
        XposedHelpers.findAndHookMethod(Cls_BillListActivity, classLoader, "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
//                            XposedBridge.log("BillListActivity onResume")
                            HookedApp.billListActivityResume = true
                            HookedApp.billListActivity = WeakReference(param.thisObject)

                            //Get BillListAdapter
                            val adapter = XposedHelpers.getObjectField(param.thisObject, "z")
                            val clsName = adapter?.javaClass?.name
                            if (clsName != Cls_BillListAdapter) {
                                XposedBridge.log("Could not find BillListAdapter:$clsName")
                                return
                            }
                            //Get adapter data
                            val data = XposedHelpers.getObjectField(adapter, "a")
//                        XposedBridge.log("Adapter data:$data")
                            HookedApp.billSingleListItems.addAll(data as Collection<Any>)

                            //ListView
                            val listView = XposedHelpers.getObjectField(param.thisObject, "d") as ListView
                            checkNextBill(listView)
                        } catch (e: Throwable) {
                            XposedBridge.log(e)
                            HookedApp.app?.toast("Parse BillListActivity error")
                        }
                    }
                })
        XposedHelpers.findAndHookMethod(Cls_BillListActivity, classLoader, "onPause",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        HookedApp.billListActivityResume = false
                    }
                })
    }

    /**
     * 检测是否还有未处理的订单，若是则跳转，否则toast。
     */
    fun checkNextBill(listView: ListView) {
//        XposedBridge.log("listview:$listView")
        goNextH5()?.let {
            if (!it) {
                HookedApp.app?.toast("账单数据处理完成")
            }
        }
    }

    /**
     * 根据页面情况跳转到订单详情
     * @return 是否正确跳转。若null说明此时页面暂停状态
     */
    fun goNextH5(): Boolean? {
        if (HookedApp.billListActivityResume) {
//            XposedBridge.log("billSingleListItems: ${HookedApp.billSingleListItems}")
            HookedApp.billSingleListItems.find {
                isItemUndo(it)
            }?.let {
                goBillH5Page(it)
                return true
            }
            return false
        } else {
            XposedBridge.log("BillListActivity paused")
        }
        return null
    }

    /**
     * item是否未被处理过。即是否已解析保存在数据库中。
     * @param item SingleListItem
     */
    fun isItemUndo(item: Any): Boolean {
        val tradeNo = XposedHelpers.getObjectField(item, "bizInNo") as String?
        if (tradeNo.isNullOrEmpty()) {
            return false
        }
        val uri = BillH5ContentValues.getItemUri(tradeNo!!)
        val cursor = HookedApp.app?.contentResolver?.query(uri, null, null, null, null)
        val count = cursor?.count ?: 0
        cursor?.close()
        return count == 0
    }

    /**
     * 跳转到订单详情界面。
     * @param item SingleListItem
     */
    fun goBillH5Page(item: Any) {
        val activity = HookedApp.billListActivity?.get()
        if (activity != null) {
            //c(SingleListItem)
            try {
                XposedHelpers.callMethod(activity, "c", item)
            } catch (e: Throwable) {
                XposedBridge.log(e)
            }
        }
    }
}