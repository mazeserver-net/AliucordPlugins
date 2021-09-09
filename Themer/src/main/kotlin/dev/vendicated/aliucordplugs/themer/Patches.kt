/*
 * Ven's Aliucord Plugins
 * Copyright (C) 2021 Vendicated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 * http://www.apache.org/licenses/LICENSE-2.0
*/

package dev.vendicated.aliucordplugs.themer

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.*
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.aliucord.*
import com.aliucord.api.PatcherAPI
import com.aliucord.fragments.SettingsPage
import com.aliucord.patcher.PinePatchFn
import com.aliucord.patcher.PinePrePatchFn
import com.aliucord.wrappers.messages.AttachmentWrapper.Companion.filename
import com.aliucord.wrappers.messages.AttachmentWrapper.Companion.url
import com.discord.app.AppFragment
import com.discord.utilities.color.ColorCompat
import com.discord.widgets.chat.list.actions.WidgetChatListActions
import com.google.android.material.textfield.TextInputLayout
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.lytefast.flexinput.R
import rx.functions.Action1
import top.canyie.pine.Pine.CallFrame
import top.canyie.pine.callback.MethodHook
import java.io.*
import java.util.regex.Pattern


fun addPatches(patcher: PatcherAPI) {
    patcher.run {
        val enableTransparency = Themer.mSettings.transparencyMode != TransparencyMode.NONE
        if (enableTransparency) setBackgrounds()

        patchGetFont()
        patchGetColor()
        patchSetColor(enableTransparency)
        patchColorStateLists()
        tintDrawables()
        themeAttributes()
        themeStatusBar()
        themeTextInput()
        addDownloadButton()

        // Set text colour of transparency options in colour picker
        patchColorPicker()
    }
}

private fun PatcherAPI.setBackgrounds() {
    val containerId = Utils.getResId("widget_tabs_host_container", "id")
    val chatBgId = Utils.getResId("widget_home_panel_center_chat", "id")
    val chatId = Utils.getResId("panel_center", "id")

    patch(AppFragment::class.java.getDeclaredMethod("onViewBound", View::class.java), PinePatchFn { callFrame: CallFrame ->
        if (ResourceManager.customBg == null) return@PinePatchFn
        val clazz = callFrame.thisObject.javaClass
        val className = clazz.simpleName
        var view = callFrame.args[0] as View
        val transparencyMode = Themer.mSettings.transparencyMode
        if (className == "WidgetChatList") {
            val id = if (transparencyMode == TransparencyMode.FULL) containerId else chatId
            while (view.id != id) {
                view = view.parent as View
                if (view.id == chatBgId) view.background = null
            }
            view.background = ResourceManager.customBg
        } else if ((transparencyMode == TransparencyMode.CHAT_SETTINGS || transparencyMode == TransparencyMode.FULL) && (className.lowercase()
                .contains("settings") || SettingsPage::class.java.isAssignableFrom(clazz))
        ) {
            view.background = ResourceManager.customBg
        }
    })
}

private fun PatcherAPI.patchGetFont() {
    val fontHook = PinePrePatchFn { callFrame ->
        val font = ResourceManager.getFontForId(callFrame.args[1] as Int) ?: ResourceManager.getDefaultFont()
        font?.let {
            callFrame.result = it
        }
    }

    ResourcesCompat::class.java.declaredMethods.forEach {
        if (it.name == "loadFont") patch(it, fontHook)
    }
}

private fun PatcherAPI.patchGetColor() {
    val patchGetColor: Function1<Int, MethodHook> = { idx ->
        PinePrePatchFn { callFrame: CallFrame ->
            ResourceManager.getColorForId(callFrame.args[idx] as Int)?.let {
                callFrame.result = it
            }
        }
    }

    patch(ColorCompat::class.java.getDeclaredMethod("getThemedColor", Context::class.java, Int::class.javaPrimitiveType), patchGetColor.invoke(1))
    patch(Resources::class.java.getDeclaredMethod("getColor", Int::class.javaPrimitiveType, Resources.Theme::class.java), patchGetColor.invoke(0))
}

private fun PatcherAPI.patchSetColor(enableTransparency: Boolean) {
    patch(
        ColorDrawable::class.java.getDeclaredMethod("setColor", Int::class.javaPrimitiveType),
        PinePrePatchFn(if (enableTransparency) Action1 { callFrame: CallFrame ->
            ResourceManager.getNameByColor(callFrame.args[0] as Int)?.let { name ->
                ResourceManager.getColorForName(name)?.let { color ->
                    callFrame.args[0] = color
                }
            }
        } else Action1 { callFrame: CallFrame ->
            ResourceManager.getColorReplacement(callFrame.args[0] as Int)?.let {
                callFrame.args[0] = it
            }
        })
    )
}

private fun PatcherAPI.patchColorStateLists() {
    // Figure out better way to do this
    // This is stupid, because it matches the wrong name because we dont work with ids here but rather the colour value so its
    // impossible to consistently resolve the correct name
    patch(
        ColorStateList::class.java.getDeclaredMethod("getColorForState", IntArray::class.java, Int::class.javaPrimitiveType),
        PinePatchFn { callFrame: CallFrame ->
            ResourceManager.getColorReplacement(callFrame.result as Int)?.let {
                callFrame.result = it
            }
        })
}

private fun PatcherAPI.tintDrawables() {
    patch(
        Resources::class.java.getDeclaredMethod(
            "getDrawableForDensity",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Resources.Theme::class.java
        ), PinePatchFn { callFrame: CallFrame ->
            ResourceManager.getDrawableTintForId(callFrame.args[0] as Int)?.let {
                (callFrame.result as Drawable?)?.setTint(it)
            }
        })
}

private fun PatcherAPI.themeAttributes() {
    patch(
        Resources.Theme::class.java.getDeclaredMethod(
            "resolveAttribute",
            Int::class.javaPrimitiveType,
            TypedValue::class.java,
            Boolean::class.javaPrimitiveType
        ), PinePatchFn { callFrame: CallFrame ->
            ResourceManager.getAttrForId(callFrame.args[0] as Int)?.let {
                (callFrame.args[1] as TypedValue).data = it
            }
        })
}

private fun PatcherAPI.themeStatusBar() {
    patch(
        ColorCompat::class.java.getDeclaredMethod(
            "setStatusBarColor",
            Window::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ), PinePrePatchFn { callFrame ->
            ResourceManager.getColorForName("statusbar")?.let {
                callFrame.args[1] = it
            }
        })
}

private fun PatcherAPI.themeTextInput() {
    patch(TextInputLayout::class.java.getDeclaredMethod("calculateBoxBackgroundColor"), PinePrePatchFn { callFrame: CallFrame ->
        ResourceManager.getColorForName("input_background")?.let {
            callFrame.result = it
        }
    })
}

const val THEMES_CHANNEL_ID = 824357609778708580L

@SuppressLint("SetTextI18n")
private fun PatcherAPI.addDownloadButton() {
    val viewId = View.generateViewId()
    val badUrlMatcher = Pattern.compile("http[^\\s]+\\.json")

    patch(
        WidgetChatListActions::class.java,
        "configureUI",
        arrayOf<Class<*>>(WidgetChatListActions.Model::class.java),
        PinePatchFn { callFrame: CallFrame ->
            val layout = ((callFrame.thisObject as WidgetChatListActions).requireView() as ViewGroup).getChildAt(0) as ViewGroup?
            if (layout == null || layout.findViewById<View?>(viewId) != null) return@PinePatchFn
            val ctx = layout.context
            val msg = (callFrame.args[0] as WidgetChatListActions.Model).message
            if (msg.channelId == THEMES_CHANNEL_ID) {
                var url: String? = null
                var name: String? = null
                msg.attachments.forEach {
                    if (it.url.endsWith(".json")) {
                        url = it.url
                        name = it.filename
                    }
                }
                if (url == null && msg.content != null) {
                    badUrlMatcher.matcher(msg.content).run {
                        if (find()) {
                            url = group()
                            name = url!!.substringAfterLast('/')
                        }
                    }
                }

                url?.let {
                    TextView(ctx, null, 0, R.h.UiKit_Settings_Item_Icon).run {
                        id = viewId
                        text = "Install $name"
                        ContextCompat.getDrawable(ctx, R.d.ic_theme_24dp)?.let {
                            it.setTint(ColorCompat.getThemedColor(ctx, R.b.colorInteractiveNormal))
                            setCompoundDrawablesRelativeWithIntrinsicBounds(it, null, null, null)
                        }

                        setOnClickListener {
                            Utils.threadPool.execute {
                                try {
                                    Http.Request(url).execute().run {
                                        saveToFile(File(THEME_DIR, name!!))
                                        ThemeLoader.loadThemes(false)
                                        Utils.showToast(ctx, "Successfully installed theme $name")
                                    }
                                } catch (ex: Throwable) {
                                    logger.error(ctx, "Failed to install theme $name", ex)
                                }
                            }
                        }
                        layout.addView(this, 1)
                    }
                }
            }
        })
}

private fun PatcherAPI.patchColorPicker() {
    /*
     * Discord does not use transparency so it does not get themed
     * This method is createPresetsView: https://github.com/discord/ColorPicker/blob/master/library/src/main/java/com/jaredrummler/android/colorpicker/ColorPickerDialog.java#L553
     * Wrapped into try catch so the plugin still works even if this method ever changes
     */
    try {
        patch(ColorPickerDialog::class.java.getDeclaredMethod("j"),
            PinePatchFn { cf ->
                val bundle = (cf.thisObject as ColorPickerDialog).arguments ?: return@PinePatchFn

                val view = cf.result as View
                val color = bundle.getInt("customButtonTextColor")
                val font = ResourcesCompat.getFont(view.context, bundle.getInt("buttonFont"))

                arrayOf(
                    com.jaredrummler.android.colorpicker.R.c.transparency_text,
                    com.jaredrummler.android.colorpicker.R.c.transparency_title
                ).forEach {
                    view.findViewById<TextView>(it)?.run {
                        setTextColor(color)
                        font?.let {
                            typeface = font
                        }
                    }
                }
            })
    } catch (th: Throwable) {
    }
}