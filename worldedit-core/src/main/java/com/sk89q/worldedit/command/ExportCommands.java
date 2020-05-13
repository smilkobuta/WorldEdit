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
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.operation.*;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
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
        max = 9
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
        int from = 1;
        int to = -1;
        String worldname = "exportworldmod";

        try {
            x1 = args.getInteger(0);
            y1 = args.getInteger(1);
            z1 = args.getInteger(2);
            x2 = args.getInteger(3);
            y2 = args.getInteger(4);
            z2 = args.getInteger(5);
            max_block_length = args.getInteger(6);
            if (args.argsLength() > 7) {
                from = args.getInteger(7);
            }
            if (args.argsLength() > 8) {
                to = args.getInteger(8);
            }
        } catch (Exception e) {
            e.printStackTrace();
            player.print("[EXPWORLD] Wrong usage! Usage:");
            player.print("[EXPWORLD] /worldexport x1 y1 z1 x2 y2 z2 max_block_length [from] [to]");
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

            prop.setProperty("worldname", worldname);
            prop.setProperty("num_x", String.valueOf(num_x));
            prop.setProperty("num_y", String.valueOf(num_y));
            prop.setProperty("num_z", String.valueOf(num_z));

            Date startTime = new Date();
            int count = 0;
            int total_count = num_x * num_y * num_z;
            int range_count = 0;
            int range_total_count = total_count;

            if (from > 1) {
                range_total_count -= from - 1;
            }
            if (to != -1) {
                range_total_count -= total_count - to;
            }

            player.print("§e" + from + "-" + to + "/" + total_count + "(" + range_total_count + ")");

            ExportOperation firstOperation = null;
            ExportOperation prevOperation = null;

            loopX: for (int i = 0; i < num_x; i++) {
                for (int j = 0; j < num_y; j++) {
                    for (int k = 0; k < num_z; k++) {
                        // Export
                        String posid = i + "_" + j + "_" + k;
                        count++;

                        String[] fromTo = calculate_coordinates(coordinate1_list, coordinate2_list, i, j, k, max_block_length);

                        // For import
                        destPosList.add(fromTo[0] + "~" + fromTo[1]);

                        if (count < from) {
                            System.out.println("skip posid=" + posid + " count=" + count + "/" + total_count);
                            continue;
                        } else if (to != -1 && count > to) {
                            System.out.println("skip posid=" + posid + " count=" + count + "/" + total_count);
                            continue;
                        }

                        range_count++;

                        ExportOperation exportOperation = new ExportOperation(startTime, posid, fromTo[0], fromTo[1], count
                                , total_count, range_count, range_total_count, worldname);
                        if (firstOperation == null) {
                            firstOperation = exportOperation;
                            firstOperation.setVars(player, session, editSession);
                        }
                        if (prevOperation != null) {
                            prevOperation.setNext(exportOperation);
                        }
                        prevOperation = exportOperation;
                    }
                }
            }

            prop.setProperty("exported_coordinates", join(destPosList.toArray(new String[0]), ","));
            prop.store(bos, "no-comment");

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
            int range_count = 0;
            int range_total_count = total_count;

            if (from > 1) {
                range_total_count -= from - 1;
            }
            if (to != -1) {
                range_total_count -= total_count - to;
            }

            player.print("§e" + from + "-" + to + "/" + total_count + "(" + range_total_count + ")");

            Date startTime = new Date();
            ImportOperation firstOperation = null;
            ImportOperation prevOperation = null;

            loopX: for (int i = 0; i < num_x; i++) {
                for (int j = 0; j < num_y; j++) {
                    for (int k = 0; k < num_z; k++) {
                        String posid = i + "_" + j + "_" + k;
                        String[] fromTo = dst_pos_list[count].split("~");
                        count ++;

                        if (count < from) {
                            System.out.println("skip posid=" + posid + " count=" + count + "/" + total_count);
                            continue;
                        } else if (to != -1 && count > to) {
                            System.out.println("done");
                            break loopX;
                        }

                        range_count ++;

                        ImportOperation importOperation = new ImportOperation(startTime, posid, fromTo[0], fromTo[1], count
                                , total_count, range_count, range_total_count, worldname);
                        if (firstOperation == null) {
                            firstOperation = importOperation;
                            firstOperation.setVars(player, session, editSession);
                        }
                        if (prevOperation != null) {
                            prevOperation.setNext(importOperation);
                        }
                        prevOperation = importOperation;
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

    class ExportOperation implements Operation {
        private ExportOperation next;

        private Date startTime;
        private String from;
        private String to;
        private String posid;
        private int count;
        private int total_count;
        private int range_count;
        private int range_total_count;
        private Player player;
        private LocalSession session;
        private String worldname;
        private EditSession editSession;
        private Operation operation;

        public ExportOperation(Date startTime, String posid, String from, String to, int count, int total_count
                , int range_count, int range_total_count, String worldname) {
            this.startTime = startTime;
            this.from = from;
            this.to = to;
            this.posid = posid;
            this.count = count;
            this.total_count = total_count;
            this.range_count = range_count;
            this.range_total_count = range_total_count;
            this.worldname = worldname;
        }

        public void setNext(ExportOperation next) {
            this.next = next;
        }

        public void setVars(Player player, LocalSession session, EditSession editSession) {
            this.player = player;
            this.session = session;
            this.editSession = editSession;
        }

        @Override
        public Operation resume(RunContext run) throws WorldEditException {
            System.out.println("ExportOperation resume");
            System.out.println("posid=" + posid + " count=" + count + "/" + total_count + " range_count=" + range_count + "/" + range_total_count);
            System.out.println("from=" + from + " to=" + to);

            try {
                editSession.disableQueue();
                editSession.getReorderExtent().setEnabled(false);

                SelectionCommands selection = new SelectionCommands(worldEdit);
                CommandContext pos1Args = new CommandContext("pos1 " + from.replace(" ", ","));

                selection.pos1(player, session, editSession, pos1Args);
//                runCommand(player, "/tp @p " + from);
//                runCommand(player, "//pos1");

                CommandContext pos2Args = new CommandContext("pos2 " + to.replace(" ", ","));
                selection.pos2(player, session, editSession, pos2Args);
//                runCommand(player, "/tp @p " + to);
//                runCommand(player, "//pos2");

                Region region = session.getSelection(player.getWorld());
                ClipboardCommands clipboard = new ClipboardCommands(worldEdit);
                operation = clipboard.copy_operation(player, session, editSession, region, false, null);
//                runCommand(player, "//copy");

                ((ForwardExtentCopy) operation).setNext(new Operation() {
                    @Override
                    public Operation resume(RunContext run) throws WorldEditException {
                        operation = null;
                        System.out.println("ExportOperation resume in setNext");

                        try {
                            SchematicCommands schematic = new SchematicCommands(worldEdit);
                            schematic.save(player, session, "schematic", worldname + "_" + posid);

//                            runCommand(player, "//schematic save " + worldname + "_" + posid);

                            // remove BaseBlock array recursive.
                            ClipboardHolder holder = session.getClipboard();
                            Clipboard clipboardR = holder.getClipboard();
                            ((BlockArrayClipboard) clipboardR).clear();
//                            session.setClipboard(null);
//
//                            clipboard.clearClipboard(player, session, editSession);
//                            session.clearHistory();
                            WorldEdit.getInstance().getSessionManager().clear();

                            // Done
                            System.out.println(count + "/" + total_count + "(" + range_count + "/" + range_total_count + ")" + " done. time-left=" + timeLeft(startTime, range_total_count, range_count));
                            player.print("§e" + count + "/" + total_count + "(" + range_count + "/" + range_total_count + ")" + " §edone");
                            printMemory();

                            ExportOperation op = next;
                            ExportOperation.this.next = null;

                            if (op != null) {
                                op.setVars(player, session, editSession);
                            }

                            ExportOperation.this.player = null;
                            ExportOperation.this.session = null;
                            ExportOperation.this.editSession = null;
                            ExportOperation.this.operation = null;

                            return op;
                        } catch (CommandException e) {
                            e.printStackTrace();
                        }

                        return null;
                    }

                    @Override
                    public void cancel() {
                    }

                    @Override
                    public void addStatusMessages(List<String> messages) {
                    }
                });

                Operations.completeLegacy(operation);

            } catch (CommandException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        public void cancel() {

        }

        @Override
        public void addStatusMessages(List<String> messages) {

        }
    }

    class ImportOperation implements Operation {
        private ImportOperation next;

        private Date startTime;
        private String from;
        private String to;
        private String posid;
        private int count;
        private int total_count;
        private int range_count;
        private int range_total_count;
        private Player player;
        private LocalSession session;
        private String worldname;
        private EditSession editSession;
        private Operation operation;

        public ImportOperation(Date startTime, String posid, String from, String to, int count, int total_count
                , int range_count, int range_total_count, String worldname) {
            this.startTime = startTime;
            this.from = from;
            this.to = to;
            this.posid = posid;
            this.count = count;
            this.total_count = total_count;
            this.range_count = range_count;
            this.range_total_count = range_total_count;
            this.worldname = worldname;
        }

        public void setNext(ImportOperation next) {
            this.next = next;
        }

        public void setVars(Player player, LocalSession session, EditSession editSession) {
            this.player = player;
            this.session = session;
            this.editSession = editSession;
        }

        @Override
        public Operation resume(RunContext run) throws WorldEditException {
            System.out.println("ImportOperation resume");
            System.out.println("posid=" + posid + " count=" + count + "/" + total_count + " range_count=" + range_count + "/" + range_total_count);
            System.out.println("from=" + from + " to=" + to);

            editSession.disableQueue();
            editSession.getReorderExtent().setEnabled(false);

            // remove BaseBlock array recursive.
//            ClipboardHolder holder = session.getClipboard();
//            Clipboard clipboardR = holder.getClipboard();
//            ((BlockArrayClipboard) clipboardR).clear();
//
//            WorldEdit.getInstance().getSessionManager().clear();

//            System.out.println("tp @a " + dst_pos_list[count]);
//            runCommand(player, "say tp @a " + dst_pos_list[count]);
//            runCommand(player, "tp @a " + dst_pos_list[count]);
//            server.commandManager.executeCommand(player, "/tp @p " + dst_pos_list[count]);

            SchematicCommands schematic = new SchematicCommands(worldEdit);
            schematic.load(player, session, "schematic", worldname + "_" + posid);
//            server.commandManager.executeCommand(player, "//schematic load " + worldname + "_" + posid);

            ClipboardCommands clipboardCommand = new ClipboardCommands(worldEdit);
            operation = clipboardCommand.paste_operation(player, session, editSession, false, false, false);
//            server.commandManager.executeCommand(player, "//paste");

            ((ForwardExtentCopy) operation).setNext(new Operation() {
                @Override
                public Operation resume(RunContext run) throws WorldEditException {
                    operation = null;
                    System.out.println("ImportOperation resume in setNext");
                    clipboardCommand.clearClipboard(player, session, editSession);
                    System.out.println("editSession count=" + editSession.getBlockChangeCount() + " limit=" + editSession.getBlockChangeLimit());
                    ChangeSet changeSet = editSession.getChangeSet();
                    System.out.println("ChangeSet size=" + changeSet.size());

                    ImportOperation op = next;
                    ImportOperation.this.next = null;

                    if (op != null) {
                        op.setVars(player, session, editSession);
                    }

                    // Done
                    System.out.println(count + "/" + total_count + "(" + range_count + "/" + range_total_count + ")" + " done. time-left=" + timeLeft(startTime, range_total_count, range_count));
                    player.print("§e" + count + "/" + total_count + "(" + range_count + "/" + range_total_count + ")" + " §edone");
                    printMemory();

                    ImportOperation.this.player = null;
                    ImportOperation.this.session = null;
                    ImportOperation.this.editSession = null;
                    ImportOperation.this.operation = null;

                    return op;
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

    public void printMemory() {
        int maxMemory = Math.round(Runtime.getRuntime().maxMemory() / 1024 / 1024);
        int totalMemory = Math.round(Runtime.getRuntime().totalMemory() / 1024 / 1024);
        int freeMemory = Math.round(Runtime.getRuntime().freeMemory() / 1024 / 1024);
        int usedMemory = totalMemory - freeMemory;
        int memPercent = (int) Math.round((double) usedMemory / maxMemory * 100);
        System.out.println("Memory usage: " + memPercent + "% " + usedMemory + "/" + maxMemory + "M");
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
