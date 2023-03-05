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
import android.content.res.*
import android.graphics.*
import android.graphics.drawable.*
import android.os.*
import android.util.TypedValue
import android.view.*
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.FragmentContainerView
import com.aliucord.*
import com.aliucord.api.PatcherAPI
import com.aliucord.fragments.SettingsPage
import com.aliucord.patcher.*
import com.aliucord.utils.ReflectUtils
import com.aliucord.wrappers.messages.AttachmentWrapper.Companion.filename
import com.aliucord.wrappers.messages.AttachmentWrapper.Companion.url
import com.discord.app.*
import com.discord.databinding.WidgetChatListAdapterItemEmbedBinding
import com.discord.utilities.color.ColorCompat
import com.discord.widgets.chat.list.actions.WidgetChatListActions
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemAttachment
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemEmbed
import com.facebook.drawee.view.SimpleDraweeView
import com.google.android.material.textfield.TextInputLayout
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.lytefast.flexinput.R
import java.io.*
import java.net.URLDecoder
import java.util.regex.Pattern

fun addPatches(patcher: PatcherAPI) {
    patcher.run {
        //if (Themer.mSettings.transparencyMode != TransparencyMode.NONE) setBackgrounds()

        //if (Themer.mSettings.enableFontHook) patchGetFont()

        //if (Themer.mSettings.customSounds) patchOpenRawResource()

        //patchGetColor()
        //patchSetColor()
        //patchColorStateLists()
        //tintDrawables()
        //themeAttributes()
        themeStatusBar()
        //themeTextInput()
        //addDownloadButton()

        // Set text colour of transparency options in colour picker
        //patchColorPicker()
    }
}



// This patch somehow causes crashes for some people, I don't get it
@SuppressLint("RestrictedApi")
private fun PatcherAPI.patchGetFont() {
    patch(ResourcesCompat::class.java.getDeclaredMethod(
        "loadFont",
        Context::class.java,
        Resources::class.java,
        TypedValue::class.java,
        Int::class.javaPrimitiveType, // id
        Int::class.javaPrimitiveType, // style
        ResourcesCompat.FontCallback::class.java,
        Handler::class.java,
        Boolean::class.javaPrimitiveType, // isRequestFromLayoutInflator
        Boolean::class.javaPrimitiveType // isCachedOnly
    ), PreHook { param ->
        val font = ResourceManager.getFontForId(param.args[3] as Int) ?: ResourceManager.getDefaultFont()
        if (font != null) {
            param.result = font
            param.args[5]?.let {
                it as ResourcesCompat.FontCallback
                it.callbackSuccessAsync(font, param.args[6] as Handler?)
            }
        }
    })
}

private fun PatcherAPI.patchOpenRawResource() {
    patch(Resources::class.java.getDeclaredMethod("openRawResourceFd", Int::class.javaPrimitiveType),
        PreHook { param ->
            ResourceManager.getRawForId(param.args[0] as Int)?.let {
                param.result = AssetFileDescriptor(ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY), 0, -1)
            }
        }
    )
}

/*private fun PatcherAPI.patchGetColor() {
    patch(Resources::class.java.getDeclaredMethod("getColor", Int::class.javaPrimitiveType, Resources.Theme::class.java),
        PreHook { param ->
            ResourceManager.getColorForId(param.args[0] as Int)?.let {
                param.result = it
            }
        }
    )
}*/

private fun PatcherAPI.patchSetColor() {
    patch(
        ColorDrawable::class.java.getDeclaredMethod("setColor", Int::class.javaPrimitiveType),
        PreHook { param ->
            val color = param.args[0] as Int
            ResourceManager.getColorReplacement(color)?.let {
                param.args[0] = it
                return@PreHook
            }
            // Discord has blocked message colours HARDCODED, so this is the only way to theme it :husk:
            // I HATE DISCORD
            val isBlockedColor = (color == BLOCKED_COLOR_DARK && currentTheme != "light") || (color == BLOCKED_COLOR_LIGHT && currentTheme == "light")
            if (isBlockedColor) ResourceManager.getColorForName("blocked_bg")?.let {
                param.args[0] = it
            }
        }
    )
}

private fun PatcherAPI.patchColorStateLists() {
    // Figure out better way to do this
    // This is stupid, because it matches the wrong name because we dont work with ids here but rather the colour value so its
    // impossible to consistently resolve the correct name
    patch(
        ColorStateList::class.java.getDeclaredMethod("getColorForState", IntArray::class.java, Int::class.javaPrimitiveType),
        Hook { param ->
            ResourceManager.getColorReplacement(param.result as Int)?.let {
                param.result = it
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
        ), Hook { param ->
            ResourceManager.getDrawableTintForId(param.args[0] as Int)?.let {
                (param.result as Drawable?)?.setTint(it)
            }
        })
}

private fun PatcherAPI.themeAttributes() {
    patch(ColorCompat::class.java.getDeclaredMethod("getThemedColor", Context::class.java, Int::class.javaPrimitiveType),
        PreHook { cf ->
            ResourceManager.getAttrForId(cf.args[1] as Int)?.let {
                cf.result = it
            }
        }
    )
}

private fun PatcherAPI.themeStatusBar() {
    patch(
        ColorCompat::class.java.getDeclaredMethod(
            "setStatusBarColor",
            Window::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ), PreHook { param ->
            ResourceManager.getColorForName("statusbar")?.let {
                param.args[1] = it
            }
        })
}

private fun PatcherAPI.themeTextInput() {
    patch(TextInputLayout::class.java.getDeclaredMethod("calculateBoxBackgroundColor"), PreHook { param ->
        ResourceManager.getColorForName("input_background")?.let {
            param.result = it
        }
    })
}

const val THEME_DEV_CHANNEL = 868419532992172073L
const val THEME_SUPPORT_CHANNEL = 875213883776847873L

/*@SuppressLint("SetTextI18n")
private fun PatcherAPI.addDownloadButton() {
    val emojiTrayId = Utils.getResId("dialog_chat_actions_add_reaction_emojis_list", "id")
    val id = View.generateViewId()
    val badUrlMatcher = Pattern.compile("http[^\\s]+\\.json")

    patch(
        WidgetChatListActions::class.java,
        "configureUI",
        arrayOf<Class<*>>(WidgetChatListActions.Model::class.java),
        Hook { param ->
            val thisObj = param.thisObject as WidgetChatListActions
            val layout = (thisObj.requireView() as ViewGroup).getChildAt(0) as ViewGroup?
                ?: return@Hook

            if (layout.findViewById<View>(id) != null) return@Hook

            val idx = if (emojiTrayId == 0) 1 else layout.indexOfChild(layout.findViewById(emojiTrayId)) + 1

            val ctx = layout.context
            val msg = (param.args[0] as WidgetChatListActions.Model).message
            if (msg.channelId == Constants.THEMES_CHANNEL_ID || msg.channelId == THEME_DEV_CHANNEL || msg.channelId == THEME_SUPPORT_CHANNEL) {
                HashMap<String, String>().apply {
                    msg.attachments.forEach {
                        if (it.url.endsWith(".json")) {
                            put(it.filename, it.url)
                        }
                    }
                    badUrlMatcher.matcher(msg.content).run {
                        while (find()) {
                            val url = group()
                            val name = url.substringAfterLast('/')
                            put(name, url)
                        }
                    }
                }.forEach { (name, url) ->
                    TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Icon).run {
                        this.id = id
                        val prettyName =
                            URLDecoder.decode(name, "UTF-8")
                                .replace('_', ' ')
                                .replace('-', ' ')
                                .removeSuffix(".json")

                        text = "Install $prettyName"

                        val drawable = ContextCompat.getDrawable(ctx, R.e.ic_theme_24dp)?.mutate()?.apply {
                            setTint(ColorCompat.getThemedColor(ctx, R.b.colorInteractiveNormal))
                        }

                        setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
                        setOnClickListener {
                            thisObj.dismiss()
                            Utils.threadPool.execute {
                                try {
                                    Http.Request(url).use {
                                        it.execute().saveToFile(File(THEME_DIR, name))
                                        ThemeLoader.loadThemes(false)
                                        Utils.showToast("Successfully installed theme $prettyName")
                                    }
                                } catch (ex: Throwable) {
                                    logger.errorToast("Failed to install theme $prettyName", ex)
                                }
                            }
                        }

                        layout.addView(this, idx)
                    }
                }
            }
        })
}*/

private fun PatcherAPI.patchColorPicker() {
    /*
     * Discord does not use transparency so it does not get themed
     * This method is createPresetsView: https://github.com/discord/ColorPicker/blob/master/library/src/main/java/com/jaredrummler/android/colorpicker/ColorPickerDialog.java#L553
     * Wrapped into try catch so the plugin still works even if this method ever changes
     */
    try {
        patch(ColorPickerDialog::class.java.getDeclaredMethod("j"),
            Hook { param ->
                val bundle = (param.thisObject as ColorPickerDialog).arguments ?: return@Hook

                val view = param.result as View
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
