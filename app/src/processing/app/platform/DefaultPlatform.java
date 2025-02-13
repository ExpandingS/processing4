/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-15 The Processing Foundation
  Copyright (c) 2008-12 Ben Fry and Casey Reas

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app.platform;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.net.URI;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import com.sun.jna.Library;
import com.sun.jna.Native;

import processing.app.Base;
import processing.app.Preferences;
import processing.app.ui.Toolkit;
import processing.awt.ShimAWT;
import processing.core.PApplet;


/**
 * Used by Base for platform-specific tweaking, for instance finding the
 * sketchbook location using the Windows registry, or OS X event handling.
 *
 * The methods in this implementation are used by default, and can be
 * overridden by a subclass.
 *
 * These methods throw vanilla-flavored Exceptions, so that error handling
 * occurs inside Platform (which will show warnings in some cases).
 *
 * There is currently no mechanism for adding new platforms, as the setup is
 * not automated. We could use getProperty("os.arch") perhaps, but that's
 * debatable (could be upper/lowercase, have spaces, etc.. basically we don't
 * know if name is proper Java package syntax.)
 */
public class DefaultPlatform {

  private final String[] FONT_SCALING_WIDGETS = {
    "Button",
    "CheckBox",
    "CheckBoxMenuItem",
    "ComboBox",
    "List",
    "Menu",
    "MenuBar",
    "MenuItem",
    "OptionPane",
    "Panel",
    "PopupMenu",
    "ProgressBar",
    "RadioButton",
    "RadioButtonMenuItem",
    "ScrollPane",
    "TabbedPane",
    "Table",
    "TableHeader",
    "TextArea",
    "TextPane",
    "TitledBorder",
    "ToggleButton",
    "ToolBar",
    "ToolTip",
    "Tree",
    "Viewport"
  };

  Base base;


  public void initBase(Base base) {
    this.base = base;
  }


  /**
   * Set the default L & F. While I enjoy the bounty of the sixteen possible
   * exception types that this UIManager method might throw, I feel that in
   * just this one particular case, I'm being spoiled by those engineers
   * at Sun, those Masters of the Abstractionverse. So instead, I'll pretend
   * that I'm not offered eleven dozen ways to report to the user exactly what
   * went wrong, and I'll bundle them all into a single catch-all "Exception".
   * Because in the end, all I really care about is whether things worked or
   * not. And even then, I don't care.
   * @throws Exception Just like I said.
   */
  public void setLookAndFeel() throws Exception {
    String laf = Preferences.get("editor.laf");
    if (laf == null || laf.length() == 0) {  // normal situation
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } else {
      UIManager.setLookAndFeel(laf);
    }

    // If the default has been overridden in the preferences, set the font
    String fontName = Preferences.get("ui.font.family");
    int fontSize = Preferences.getInteger("ui.font.size");
    if (!"Dialog".equals(fontName) || fontSize != 12) {
      setUIFont(new FontUIResource(fontName, Font.PLAIN, fontSize));
//      Map<TextAttribute, Object> attributes = new HashMap<>();
//      attributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
//      Font font = new Font(fontName, Font.PLAIN, fontSize).deriveFont(attributes);
//      setUIFont(new FontUIResource(font));
    }

    // Default was 8x8, but that's not enough with the insets and rounded rect
    // https://github.com/processing/processing4/issues/473
    //System.out.println(UIManager.get("ScrollBar.minimumThumbSize"));
    UIManager.put("ScrollBar.minimumThumbSize", new Dimension(8, 24));
  }


  // Rewritten from https://stackoverflow.com/a/7434935
  static private void setUIFont(FontUIResource f) {
    for (Object key : UIManager.getLookAndFeelDefaults().keySet()) {
      Object value = UIManager.get(key);
      if (value instanceof FontUIResource) {
        UIManager.put(key, f);
      }
    }
  }


  public void setInterfaceZoom() throws Exception {
    // Specify font when scaling is active.
    if (!Preferences.getBoolean("editor.zoom.auto")) {
      for (String widgetName : FONT_SCALING_WIDGETS) {
        scaleDefaultFont(widgetName);
      }

      String fontName = Preferences.get("ui.font.family");
      int fontSize = Preferences.getInteger("ui.font.size");
      FontUIResource uiFont = new FontUIResource(fontName, Font.PLAIN, Toolkit.zoom(fontSize));
      UIManager.put("Label.font", uiFont);
      UIManager.put("TextField.font", uiFont);
    }
  }


  /**
   * Handle any platform-specific languages saving. This is necessary on OS X
   * because of how bundles are handled, but perhaps your platform would like
   * to Think Different too?
   * @param languageCode 2-digit lowercase ISO language code
   */
  public void saveLanguage(String languageCode) { }


  /**
   * This function should throw an exception or return a value.
   * Do not return null.
   */
  public File getSettingsFolder() throws Exception {
    File override = Base.getSettingsOverride();
    if (override != null) {
      return override;
    }

    // If no subclass has a behavior, default to making a
    // ".processing" directory in the user's home directory.
    File home = new File(System.getProperty("user.home"));
    return new File(home, ".processing");
  }


  /**
   * @return if not overridden, a folder named "sketchbook" in user.home.
   * @throws Exception so that subclasses can throw a fit
   */
  public File getDefaultSketchbookFolder() throws Exception {
    return new File(System.getProperty("user.home"), "sketchbook");
  }


  // TODO this should be openLink(), as in PApplet, but need to look
  //      into what else it might break by changing it [fry 220202]
  public void openURL(String url) throws Exception {
    if (!ShimAWT.openLink(url)) {
      PApplet.launch(url);
    }
  }


  public boolean openFolderAvailable() {
    return Desktop.isDesktopSupported() &&
      Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
  }


  public void openFolder(File file) throws Exception {
    // TODO Looks like this should instead be Action.BROWSE_FILE_DIR,
    //      which was added in Java 9. (Also update available method.)
    Desktop.getDesktop().open(file);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public interface CLibrary extends Library {
    CLibrary INSTANCE = Native.load("c", CLibrary.class);
    int setenv(String name, String value, int overwrite);
    String getenv(String name);
    int unsetenv(String name);
    int putenv(String string);
  }


  public void setenv(String variable, String value) {
    CLibrary clib = CLibrary.INSTANCE;
    clib.setenv(variable, value, 1);
  }


  public String getenv(String variable) {
    CLibrary clib = CLibrary.INSTANCE;
    return clib.getenv(variable);
  }


  public int unsetenv(String variable) {
    CLibrary clib = CLibrary.INSTANCE;
    return clib.unsetenv(variable);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  /**
   * Get the zoom or display scaling requested by the operating system.
   *
   * <p>
   * Get the operating system zoom setting that Processing may use to resize
   * internal elements depending on user preferences. Note that some operating
   * systems will perform zooming in a way that is transparent to the
   * the applications. If that is the case, this will return 1. Otherwise,
   * the operating system will not automatically resize UI elements and this
   * will return a value other than 1, meaning that Processing may need to
   * resize elements on its own depending on user preferences.
   * </p>
   *
   * <p>
   * Note that this may be distinct from the system DPI and some operating
   * systems may report a DPI of 96 while also requesting a display zooming of
   * 125%. However, others may not report a "display scaling" but provide a
   * DPI of 120 when the elements should have a 125% zoom. This will use the
   * preferred method of determining the appropriate zoom given the platform
   * in use. Using this system display scaling percentage approach instead of
   * returning DPI directly is preferred after JEP 263.
   * </p>
   *
   * @return The zoom level where 1.0 means 100% (no zoom) and 1.25 means
   *    125% (25% additional zoom).
   */
  public float getSystemZoom() {
    return 1;
  }



  /**
   * Set the default font for the widget by the given name.
   *
   * @param name The name of the widget whose font will be set to a scaled version of its current
   *    default font in the selected look and feel. This must match the system widget name like
   *    "Button" or "CheckBox"
   */
  private void scaleDefaultFont(String name) {
    String fontPropertyName = name + ".font";

    Font currentFont = (Font) UIManager.get(fontPropertyName);
//    System.out.println(currentFont);
    float newSize = Toolkit.zoom(currentFont.getSize());
//    System.out.println(newSize);
    Font newFont = currentFont.deriveFont(newSize);
//    System.out.println(newFont);

    UIManager.put(fontPropertyName, newFont);
  }
}
