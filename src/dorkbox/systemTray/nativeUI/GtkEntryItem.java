/*
 * Copyright 2014 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.systemTray.nativeUI;

import java.io.File;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

import dorkbox.systemTray.Action;
import dorkbox.systemTray.jna.linux.GCallback;
import dorkbox.systemTray.jna.linux.Gobject;
import dorkbox.systemTray.jna.linux.Gtk;
import dorkbox.systemTray.util.ImageUtils;

class GtkEntryItem extends GtkEntry implements GCallback {
    private static File transparentIcon = null;

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final NativeLong nativeLong;

    // these have to be volatile, because they can be changed from any thread
    private volatile Action callback;
    private volatile Pointer image;

    // these are necessary BECAUSE GTK menus look funky as hell when there are some menu entries WITH icons and some WITHOUT
    protected volatile boolean hasLegitIcon = true;

    // The mnemonic will ONLY show-up once a menu entry is selected. IT WILL NOT show up before then!
    // AppIndicators will only show if you use the keyboard to navigate
    // GtkStatusIconTray will show on mouse+keyboard movement
    private volatile char mnemonicKey = 0;

    /**
     * called from inside dispatch thread. ONLY creates the menu item, but DOES NOT attach it!
     * this is a FLOATING reference. See: https://developer.gnome.org/gobject/stable/gobject-The-Base-Object-Type.html#floating-ref
     */
    GtkEntryItem(final GtkMenu parent, final Action callback) {
        super(parent, Gtk.gtk_image_menu_item_new_with_mnemonic(""));
        this.callback = callback;

        // cannot be done in a static initializer, because the tray icon size might not yet have been determined
        if (transparentIcon == null) {
            transparentIcon = ImageUtils.getTransparentImage(ImageUtils.ENTRY_SIZE);
        }

        if (callback != null) {
            Gtk.gtk_widget_set_sensitive(_native, Gtk.TRUE);
            nativeLong = Gobject.g_signal_connect_object(_native, "activate", this, null, 0);
        }
        else {
            Gtk.gtk_widget_set_sensitive(_native, Gtk.FALSE);
            nativeLong = null;
        }
    }

    @Override
    public
    void setShortcut(final char key) {
        this.mnemonicKey = Character.toLowerCase(key);

        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                renderText(getText());
            }
        });
    }

    @Override
    public
    void setCallback(final Action callback) {
        this.callback = callback;
    }

    // called by native code
    @Override
    public
    int callback(final Pointer instance, final Pointer data) {
        final Action cb = this.callback;
        if (cb != null) {
            Gtk.proxyClick(getParent(), GtkEntryItem.this, cb);
        }

        return Gtk.TRUE;
    }

    @Override
    public
    boolean hasImage() {
        return hasLegitIcon;
    }

    /**
     * the menu entry looks FUNKY when there are a mis-match of entries WITH and WITHOUT images.
     * This is primarily only with AppIndicators, although not always.
     * <p>
     * called on the DISPATCH thread
     */
    void setSpacerImage(final boolean everyoneElseHasImages) {
        if (hasLegitIcon) {
            // we have a legit icon, so there is nothing else we can do.
            return;
        }

        if (image != null) {
            Gtk.gtk_widget_destroy(image);
            image = null;
            Gtk.gtk_widget_show_all(_native);
        }

        if (everyoneElseHasImages) {
            image = Gtk.gtk_image_new_from_file(transparentIcon.getAbsolutePath());
            Gtk.gtk_image_menu_item_set_image(_native, image);

            //  must always re-set always-show after setting the image
            Gtk.gtk_image_menu_item_set_always_show_image(_native, Gtk.TRUE);
        }

        Gtk.gtk_widget_show_all(_native);
    }

    /**
     * must always be called in the GTK thread
     */
    void renderText(String text) {
        if (this.mnemonicKey != 0) {
            // they are CASE INSENSITIVE!
            int i = text.toLowerCase()
                        .indexOf(this.mnemonicKey);

            if (i >= 0) {
                text = text.substring(0, i) + "_" + text.substring(i);
            }
        }

        Gtk.gtk_menu_item_set_label(_native, text);
        Gtk.gtk_widget_show_all(_native);
    }

    // NOTE: XFCE used to use appindicator3, which DOES NOT support images in the menu. This change was reverted.
    // see: https://ask.fedoraproject.org/en/question/23116/how-to-fix-missing-icons-in-program-menus-and-context-menus/
    // see: https://git.gnome.org/browse/gtk+/commit/?id=627a03683f5f41efbfc86cc0f10e1b7c11e9bb25
    void setImage_(final File imageFile) {
        hasLegitIcon = imageFile != null;

        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                if (image != null) {
                    Gtk.gtk_widget_destroy(image);
                    image = null;
                    Gtk.gtk_widget_show_all(_native);
                }

                if (imageFile != null) {
                    image = Gtk.gtk_image_new_from_file(imageFile.getAbsolutePath());
                    Gtk.gtk_image_menu_item_set_image(_native, image);

                    //  must always re-set always-show after setting the image
                    Gtk.gtk_image_menu_item_set_always_show_image(_native, Gtk.TRUE);
                }

                Gtk.gtk_widget_show_all(_native);
            }
        });
    }

    void removePrivate() {
        callback = null;

        if (image != null) {
            Gtk.gtk_widget_destroy(image);
            image = null;
        }
    }
}