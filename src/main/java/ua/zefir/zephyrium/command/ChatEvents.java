package ua.zefir.zephyrium.command;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public final class ChatEvents {
  private ChatEvents() {}

  public static HoverEvent showText(Component text) {
    /*? if >=1.21.5 {*/
    return new HoverEvent.ShowText(text);
    /*?} else {*/
    /*return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
    *//*?}*/
  }

  public static ClickEvent runCommand(String command) {
    /*? if >=1.21.5 {*/
    return new ClickEvent.RunCommand(command);
    /*?} else {*/
    /*return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
    *//*?}*/
  }

  public static ClickEvent copyToClipboard(String text) {
    /*? if >=1.21.5 {*/
    return new ClickEvent.CopyToClipboard(text);
    /*?} else {*/
    /*return new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text);
    *//*?}*/
  }
}
