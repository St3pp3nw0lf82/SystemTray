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
package dorkbox.systemTray.ui.swing;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;

import dorkbox.jna.linux.GtkEventDispatch;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Tray;
import dorkbox.util.OS;
import dorkbox.util.SwingUtil;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class for handling all system tray interaction, via Swing.
 *
 * It doesn't work well AT ALL on linux. See bugs:
 * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6267936
 * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6453521
 * https://stackoverflow.com/questions/331407/java-trayicon-using-image-with-transparent-background/3882028#3882028
 */
@SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "WeakerAccess"})
public final
class _SwingTray extends Tray {
    private volatile SystemTray tray;
    private volatile TrayIcon trayIcon;

    // is the system tray visible or not.
    private volatile boolean visible = true;
    private volatile File imageFile;
    private volatile String tooltipText = "";

    public static final Logger logger = LogManager.getLogger(dorkbox.systemTray.ui.swing._SwingTray.class);

    // Called in the EDT
    public
    _SwingTray(final dorkbox.systemTray.SystemTray systemTray) {
        super(systemTray);
        logger.debug("_SwingTray constructor called");
        if (!SystemTray.isSupported()) {
            logger.debug("SystemTray not supported!");
            throw new RuntimeException("System Tray is not supported in this configuration! Please write an issue and include your OS " +
                                       "type and configuration");
        } else {
            logger.debug("SystemTray supported!");
        }

        // we override various methods, because each tray implementation is SLIGHTLY different. This allows us customization.
        final SwingMenu swingMenu = new SwingMenu(null, null) {
            @Override
            public
            void setEnabled(final MenuItem menuItem) {
                SwingUtil.invokeLater(new Runnable() {
                    @Override
                    public
                    void run() {
                        logger.debug("here 1");
                        if (tray == null) {
                            tray = SystemTray.getSystemTray();
                        }
                        logger.debug("here 2");
                        boolean enabled = menuItem.getEnabled();

                        if (visible && !enabled) {
                            tray.remove(trayIcon);
                            visible = false;
                            logger.debug("here 3");
                        }

                        else if (!visible && enabled) {
                            try {
                                tray.add(trayIcon);
                                visible = true;

                                // don't want to matter which (setImage/setTooltip/setEnabled) is done first, and if the image/enabled is changed, we
                                // want to make sure keep the tooltip text the same as before.
                                trayIcon.setToolTip(tooltipText);
                                logger.debug("here 4");
                            } catch (AWTException e) {
                                dorkbox.systemTray.SystemTray.logger.error("Error adding the icon back to the tray", e);
                            }
                        }
                    }
                });
            }

            @Override
            public
            void setImage(final MenuItem menuItem) {
                imageFile = menuItem.getImage();
                if (imageFile == null) {
                    return;
                }

                SwingUtil.invokeLater(new Runnable() {
                    @Override
                    public
                    void run() {
                        if (tray == null) {
                            tray = SystemTray.getSystemTray();
                        }

                        // stupid java won't scale it right away, so we have to do this twice to get the correct size
                        final Image trayImage = new ImageIcon(imageFile.getAbsolutePath()).getImage();
                        trayImage.flush();

                        if (trayIcon == null) {
                            // here we init. everything
                            trayIcon = new TrayIcon(trayImage);

                            JPopupMenu popupMenu = (JPopupMenu) _native;
                            popupMenu.pack();
                            popupMenu.setFocusable(true);

                            // appindicators DO NOT support anything other than PLAIN gtk-menus, which do not support tooltips
                            if (tooltipText != null && !tooltipText.isEmpty()) {
                                trayIcon.setToolTip(tooltipText);
                            }

                            trayIcon.addMouseListener(new MouseAdapter() {
                                @Override
                                public
                                void mousePressed(MouseEvent e) {
                                    TrayPopup popupMenu = (TrayPopup) _native;
                                    popupMenu.doShow(e.getPoint(), 0);
                                }
                            });

                            try {
                                tray.add(trayIcon);
                            } catch (AWTException e) {
                                dorkbox.systemTray.SystemTray.logger.error("TrayIcon could not be added.", e);
                            }
                        } else {
                            trayIcon.setImage(trayImage);
                        }

                        // don't want to matter which (setImage/setTooltip/setEnabled) is done first, and if the image/enabled is changed, we
                        // want to make sure keep the tooltip text the same as before.
                        trayIcon.setToolTip(tooltipText);

                        ((TrayPopup) _native).setTitleBarImage(imageFile);
                    }
                });
            }

            @Override
            public
            void setText(final MenuItem menuItem) {
                // no op
            }

            @Override
            public
            void setShortcut(final MenuItem menuItem) {
                // no op
            }

            @Override
            public
            void setTooltip(final MenuItem menuItem) {
                final String text = menuItem.getTooltip();

                if (tooltipText != null && tooltipText.equals(text) ||
                    tooltipText == null && text != null) {
                    return;
                }

                tooltipText = text;

                SwingUtil.invokeLater(new Runnable() {
                    @Override
                    public
                    void run() {
                        // don't want to matter which (setImage/setTooltip/setEnabled) is done first, and if the image/enabled is changed, we
                        // want to make sure keep the tooltip text the same as before.
                        if (trayIcon != null) {
                            trayIcon.setToolTip(text);
                        }
                    }
                });
            }

            @Override
            public
            void remove() {
                SwingUtil.invokeLater(new Runnable() {
                    @Override
                    public
                    void run() {
                        if (trayIcon != null) {
                            if (tray != null) {
                                tray.remove(trayIcon);
                            }
                            trayIcon = null;
                        }

                        tray = null;
                    }
                });

                super.remove();


                if (OS.isLinux() || OS.isUnix()) {
                    // does not need to be called on the dispatch (it does that). Startup happens in the SystemTray (in a special block),
                    // because we MUST startup the system tray BEFORE to access GTK before we create the swing version (to get size info)
                    GtkEventDispatch.shutdownGui();
                }
            }
        };
        logger.debug("here 5");
        bind(swingMenu, null, systemTray);
    }

    @Override
    public
    boolean hasImage() {
        return imageFile != null;
    }
}
