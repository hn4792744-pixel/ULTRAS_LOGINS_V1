package me.uc_hussein.ultraslogin.common.bridge;

import java.util.List;

/** A menu description. The proxy owns all logic; the bridge only draws it and reports clicks. title is Adventure JSON. */
public record GuiModel(long id, String title, int rows, boolean closable, List<GuiItemModel> items) {
}
