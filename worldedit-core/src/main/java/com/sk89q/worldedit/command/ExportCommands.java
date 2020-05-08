/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.command;

import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import com.sk89q.worldedit.*;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.event.platform.CommandEvent;
import com.sk89q.worldedit.function.operation.*;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.io.Closer;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Export commands.
 */
public class ExportCommands {

    private final WorldEdit worldEdit;

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public ExportCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
        this.worldEdit = worldEdit;
    }

    @Command(
        aliases = { "/exportworld", "worldexport" },
        usage = "",
        desc = "Export specified area to schematic files. (use //copy and //schematic save)",
        min = 7,
        max = 7
    )
    @CommandPermissions("worldedit.export.exportworld")
    public void exportworld(Player player, LocalSession session, EditSession editSession, CommandContext args) throws WorldEditException {
        LocalConfiguration config = worldEdit.getConfiguration();

        int x1 = 0;
        int y1 = 0;
        int z1 = 0;
        int x2 = 0;
        int y2 = 0;
        int z2 = 0;
        int max_block_length = 0;
        String worldname = "exportworldmod";

        try {
            x1 = args.getInteger(0);
            y1 = args.getInteger(1);
            z1 = args.getInteger(2);
            x2 = args.getInteger(3);
            y2 = args.getInteger(4);
            z2 = args.getInteger(5);
            max_block_length = args.getInteger(6);
        } catch (Exception e) {
            e.printStackTrace();
            player.print("[EXPWORLD] Wrong usage! Usage:");
            player.print("[EXPWORLD] /exportworld x1 y1 z1 x2 y2 z2 max_block_length");
            return;
        }

        Closer closer = Closer.create();
        try {
            File dir = worldEdit.getWorkingDirectoryFile(config.saveDir);
            File f = worldEdit.getSafeSaveFile(player, dir, worldname, "properties", "properties");
            FileOutputStream fos = closer.register(new FileOutputStream(f));
            BufferedOutputStream bos = closer.register(new BufferedOutputStream(fos));
            Properties prop = new Properties();

            int[] coordinate1_list = { x1, y1, z1 };
            int[] coordinate2_list = { x2, y2, z2 };
            int diff_x = coordinate2_list[0] - coordinate1_list[0];
            int diff_y = coordinate2_list[1] - coordinate1_list[1];
            int diff_z = coordinate2_list[2] - coordinate1_list[2];

            int num_x = (int) Math.ceil(Math.abs((float) diff_x / max_block_length));
            int num_y = (int) Math.ceil(Math.abs((float) diff_y / max_block_length));
            int num_z = (int) Math.ceil(Math.abs((float) diff_z / max_block_length));

            ArrayList<String> destPosList = new ArrayList<String>();
            String from_prev = null;

            prop.setProperty("worldname", worldname);
            prop.setProperty("num_x", String.valueOf(num_x));
            prop.setProperty("num_y", String.valueOf(num_y));
            prop.setProperty("num_z", String.valueOf(num_z));

            Date startTime = new Date();
            int count = 0;
            int total_count = num_x * num_y * num_z;

            for (int i = 0; i < num_x; i++) {
                for (int j = 0; j < num_y; j++) {
                    for (int k = 0; k < num_z; k++) {
                        // Export
                        String posid = i + "_" + j + "_" + k;
                        System.out.println("posid=" + posid + " count=" + count + "/" + total_count);

                        String[] from_to = calculate_coordinates(coordinate1_list, coordinate2_list, i, j, k, max_block_length);
                        String from = from_to[0];
                        String to = from_to[1];

                        SelectionCommands selection = new SelectionCommands(worldEdit);
                        CommandContext pos1Args = new CommandContext("pos1 " + from.replace(" ", ","));
                        selection.pos1(player, session, editSession, pos1Args);
//                        runCommand(player, "/tp @p " + from);
//                        runCommand(player, "//pos1");

                        CommandContext pos2Args = new CommandContext("pos2 " + to.replace(" ", ","));
                        selection.pos2(player, session, editSession, pos2Args);
//                        runCommand(player, "/tp @p " + to);
//                        runCommand(player, "//pos2");

                        Region region = session.getSelection(player.getWorld());
                        ClipboardCommands clipboard = new ClipboardCommands(worldEdit);
                        clipboard.copy(player, session, editSession, region, false, null);
//                        runCommand(player, "//copy");

                        SchematicCommands schematic = new SchematicCommands(worldEdit);
                        schematic.save(player, session, "schematic", worldname + "_" + posid);
//                        runCommand(player, "//schematic save " + worldname + "_" + posid);

//                        commandRunner.addCommand("/tp @p " + from, 300, null);
//                        commandRunner.addCommand("//pos1", 200, null);
//                        commandRunner.addCommand("/tp @p " + to, 300, null);
//                        commandRunner.addCommand("//pos2", 200, null);
//                        commandRunner.addCommand("//copy", 1000, "worldedit-copy");
//                        commandRunner.addCommand("//schematic save " + worldname + "_" + posid, 1000, "worldedit-save");

                        // Import
                        String dst_pos;
                        if (from_prev == null) {
                            dst_pos = "~0 ~0 ~0";
                        } else {
                            dst_pos = coordinate_diff(from_prev, from);
                        }
                        from_prev = from;

                        destPosList.add(from + "~" + to);

                        // Done
                        count ++;
                        System.out.println(count + "/" + total_count + " done time-left=" + timeLeft(startTime, total_count, count));
                        player.print("§e" + count + "/" + total_count + " §edone");
//                        runCommand(player, "/say §e" + count + "/" + total_count + " §edone");
//                        commandRunner.addServerCommand("/say §e" + count + "/" + total_count + " §edone", 100, null);
                    }
                }
            }

            prop.setProperty("exported_coordinates", join(destPosList.toArray(new String[0]), ","));
            prop.store(bos, "no-comment");

        } catch (IOException | CommandException e) {
            player.printError("Properties file could not written: " + e.getMessage());
        } finally {
            try {
                closer.close();
            } catch (IOException ignored) {
            }
        }

    }

    @Command(
            aliases = { "/importworld", "worldimport" },
            usage = "",
            desc = "Import schematic files which exported by worldexport. (use //schematic load and //paste)",
            min = 0,
            max = 2
    )
    @CommandPermissions("worldedit.export.exportworld")
    public void importworld(Player player, LocalSession session, EditSession editSession, CommandContext args) throws WorldEditException {
        LocalConfiguration config = worldEdit.getConfiguration();

        String worldname = "exportworldmod";

        int from = 1;
        int to = -1;

        try {
            if (args.argsLength() > 0) {
                from = args.getInteger(0);
            }
            if (args.argsLength() > 1) {
                to = args.getInteger(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            player.print("[EXPWORLD] Wrong usage! Usage:");
            player.print("[EXPWORLD] /worldimport [from] [to]");
            return;
        }

        editSession.disableQueue();

        Closer closer = Closer.create();

        try {
            File dir = worldEdit.getWorkingDirectoryFile(config.saveDir);
            File f = worldEdit.getSafeSaveFile(player, dir, worldname, "properties", "properties");
            FileInputStream fis = closer.register(new FileInputStream(f));
            BufferedInputStream bis = closer.register(new BufferedInputStream(fis));
            Properties prop = new Properties();
            prop.load(bis);

            int num_x = Integer.parseInt(prop.getProperty("num_x"));
            int num_y = Integer.parseInt(prop.getProperty("num_y"));
            int num_z = Integer.parseInt(prop.getProperty("num_z"));

            String[] dst_pos_list = prop.getProperty("exported_coordinates").split(",");

            int count = 0;
            int total_count = num_x * num_y * num_z;

            player.print("§e" + from + "-" + to);

            Date startTime = new Date();
            MyOperation firstOperation = null;
            MyOperation prevOperation = null;

            loopX: for (int i = 0; i < num_x; i++) {
                for (int j = 0; j < num_y; j++) {
                    for (int k = 0; k < num_z; k++) {
                        String posid = i + "_" + j + "_" + k;
                        String[] fromTo = dst_pos_list[count].split("~");
                        count ++;

                        if (count < from) {
                            total_count--;
                            System.out.println("skip posid=" + posid + " count=" + count + "/" + total_count);
                            continue;
                        } else if (to != -1 && count > to) {
                            total_count--;
                            System.out.println("done");
                            break loopX;
                        }

                        MyOperation myOperation = new MyOperation(startTime, posid, fromTo[0], fromTo[1], count
                                , total_count, player, session, editSession, worldname);
                        if (firstOperation == null) {
                            firstOperation = myOperation;
                        }
                        if (prevOperation != null) {
                            prevOperation.setNext(myOperation);
                        }
                        prevOperation = myOperation;
                    }
                }
            }

            if (firstOperation != null) {
                OperationQueue queue = new OperationQueue();
                queue.offer(firstOperation);
                Operations.completeLegacy(queue);
            }

        } catch (IOException e) {
            player.printError("Properties file could not written: " + e.getMessage());
        } finally {
            try {
                closer.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * @param coordinate1_list
     * @param coordinate2_list
     * @param x
     * @param y
     * @param z
     * @param max_block_length
     * @return
     */
    protected static String[] calculate_coordinates(int[] coordinate1_list, int[] coordinate2_list, int x, int y, int z, int max_block_length) {
        int diff_x = coordinate2_list[0] - coordinate1_list[0];
        int diff_y = coordinate2_list[1] - coordinate1_list[1];
        int diff_z = coordinate2_list[2] - coordinate1_list[2];
        int count_x = (int) Math.ceil(Math.abs((float) diff_x / max_block_length));
        int count_y = (int) Math.ceil(Math.abs((float) diff_y / max_block_length));
        int count_z = (int) Math.ceil(Math.abs((float) diff_z / max_block_length));
        int x_len = Math.min(Math.round((float) diff_x / count_x), max_block_length);
        int y_len = Math.min(Math.round((float) diff_y / count_y), max_block_length);
        int z_len = Math.min(Math.round((float) diff_z / count_z), max_block_length);

        Integer[] from = {
                coordinate1_list[0] + x_len * x,
                coordinate1_list[1] + y_len * y,
                coordinate1_list[2] + z_len * z
        };
        Integer[] to = {
                coordinate1_list[0] + x_len * (x + 1),
                coordinate1_list[1] + y_len * (y + 1),
                coordinate1_list[2] + z_len * (z + 1),
        };

        if (x > 0) {
            from[0] += diff_x > 0 ? 1 : -1;
        }
        if (y > 0) {
            from[1] += diff_y > 0 ? 1 : -1;
        }
        if (z > 0) {
            from[2] += diff_z > 0 ? 1 : -1;
        }
        if (x + 1 == count_x) {
            to[0] = coordinate2_list[0];
        }
        if (y + 1 == count_y) {
            to[1] = coordinate2_list[1];
        }
        if (z + 1 == count_z) {
            to[2] = coordinate2_list[2];
        }

        return new String[] {join(from, " "), join(to, " ")};
    }

    /**
     * @param coordinate_origin
     * @param coordinate
     * @return
     */
    protected static String coordinate_diff(String coordinate_origin, String coordinate) {
        String[] coordinate_origin_list = coordinate_origin.split(" ");
        String[] coordinate_list = coordinate.split(" ");
        int[] diff_pos = {
                Integer.parseInt(coordinate_list[0]) - Integer.parseInt(coordinate_origin_list[0]),
                Integer.parseInt(coordinate_list[1]) - Integer.parseInt(coordinate_origin_list[1]),
                Integer.parseInt(coordinate_list[2]) - Integer.parseInt(coordinate_origin_list[2])
        };

        if (diff_pos[0] != 0) {
            diff_pos[0] += diff_pos[0] > 0 ? -1 : 1;
        }
        if (diff_pos[1] != 0) {
            diff_pos[1] += diff_pos[1] > 0 ? -1 : 1;
        }
        if (diff_pos[2] != 0) {
            diff_pos[2] += diff_pos[2] > 0 ? -1 : 1;
        }

        String[] diff_pos_str = new String[] {
                "~" + diff_pos[0],
                "~" + diff_pos[1],
                "~" + diff_pos[2]
        };

        return join(diff_pos_str, " ");
    }

    public static String join(Object[] strings, String separator) {
        String result = "";
        for (Object s: strings) {
            if (result.length() > 0) {
                result += separator;
            }
            result += s;
        }
        return result;
    }

    public static void runCommand(Player player, String commandStr) {
        CommandEvent commandEvent = new CommandEvent(player, commandStr);
        WorldEdit.getInstance().getPlatformManager().getCommandManager().handleCommand(commandEvent);
    }

    class MyOperation implements Operation {
        private MyOperation next;

        private Date startTime;
        private String from;
        private String to;
        private String posid;
        private int count;
        private int total_count;
        private Player player;
        private LocalSession session;
        private String worldname;
        private EditSession editSession;
        private Operation operation;

        public MyOperation(Date startTime, String posid, String from, String to, int count, int total_count
                , Player player, LocalSession session
                , EditSession editSession, String worldname) {
            this.startTime = startTime;
            this.from = from;
            this.to = to;
            this.posid = posid;
            this.count = count;
            this.total_count = total_count;
            this.player = player;
            this.session = session;
            this.worldname = worldname;
            this.editSession = editSession;
        }

        public void setNext(MyOperation next) {
            this.next = next;
        }

        @Override
        public Operation resume(RunContext run) throws WorldEditException {
            System.out.println("MyOperation resume");
            System.out.println("posid=" + posid + " count=" + count + "/" + total_count);
            System.out.println("from=" + from + " to=" + to);

            editSession.disableQueue();
            editSession.reorderExtent.setEnabled(false);

//            System.out.println("tp @a " + dst_pos_list[count]);
//            runCommand(player, "say tp @a " + dst_pos_list[count]);
//            runCommand(player, "tp @a " + dst_pos_list[count]);
//            server.commandManager.executeCommand(player, "/tp @p " + dst_pos_list[count]);

            SchematicCommands schematic = new SchematicCommands(worldEdit);
            schematic.load(player, session, "schematic", worldname + "_" + posid);
//            server.commandManager.executeCommand(player, "//schematic load " + worldname + "_" + posid);

            ClipboardCommands clipboardCommand = new ClipboardCommands(worldEdit);
            operation = clipboardCommand.paste_all(player, session, editSession, false, false, false);
//            server.commandManager.executeCommand(player, "//paste");

            ((ForwardExtentCopy) operation).setNext(new Operation() {
                @Override
                public Operation resume(RunContext run) throws WorldEditException {
                    operation = null;
                    System.out.println("MyOperation resume in setNext");
                    clipboardCommand.clearClipboard(player, session, editSession);
                    System.out.println("editSession count=" + editSession.getBlockChangeCount() + " limit=" + editSession.getBlockChangeLimit());
                    ChangeSet changeSet = editSession.getChangeSet();
                    System.out.println("ChangeSet size=" + changeSet.size());
                    System.out.println(count + "/" + total_count + " done. time-left=" + timeLeft(startTime, total_count, count));
                    player.print("§e" + count + "/" + total_count + " §edone");
                    return next;
                }

                @Override
                public void cancel() {
                }

                @Override
                public void addStatusMessages(List<String> messages) {
                }
            });
            Operations.completeLegacy(operation);

            return null;
        }

        @Override
        public void cancel() {

        }

        @Override
        public void addStatusMessages(List<String> messages) {

        }
    }

    public String timeLeft(Date startTime, int totalCount, int count) {
        Date now = new Date();
        long diff = now.getTime() - startTime.getTime();
        long secPerOne = diff / count / 1000;
        long timeLeftSec = (totalCount - count) * secPerOne;
        String timeLeft = "";
        int hour = 0;
        int min = 0;
        int sec = 0;
        if (timeLeftSec >= 60 * 60) {
            hour = (int) Math.floor(timeLeftSec / 60 / 60);
            timeLeftSec -= hour * 60 * 60;
        }
        if (timeLeftSec >= 60) {
            min = (int) Math.floor(timeLeftSec / 60);
            timeLeftSec -= min * 60;
        }
        sec = (int) timeLeftSec;
        timeLeft = String.format("%02d:%02d:%02d", hour, min, sec);

        return timeLeft;
    }
}
