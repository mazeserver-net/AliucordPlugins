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
        themeAttributes()
    }
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


