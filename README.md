WorldEdit with /worldexport and /worldimport
=========

WorldEdit is Minecraft mod that turns Minecraft into an in-game map editor.

This is one of fork programs of that.
Please check [original project](https://github.com/EngineHub/WorldEdit) and learn how to compile it.

Original //copy, //paste, //schematic save, //schematic load commands works perfectly when you export or import schematic under 300 x 300 blocks.

But when you use these commands over 1000 x 1000 blocks, it uses so much memory and finished in failure.

So I implemented /worldexport and /worldimport commands to handle large area.



/worldexport
---------

Usage: `/worldexport x1 y1 z1 x2 y2 z2 max_block_length`

- x1 y1 z1: These are a coordinate which is usually specified by //pos1 command.
- x2 y2 z2: These are another coordinate which is usually specified by //pos2 command.
- max_block_length: Area length of one schematic. 200 ~ 300 is recommended.

You should run `/tp` command before running `/worldexport` command.
So that you can easily calculate and import in another minecraft world precisely.

This command exports schematic files and one properties file.

- {Minecraft install directory}/config/worldedit/schematics/exportworldmod.properties
- {Minecraft install directory}/config/worldedit/schematics/exportworldmod_0_0_0.schematic

I recommend to save those files after export is done.
These will be overwritten by next /worldexport command.



## /worldimport

Usage: `/worldimport [from] [to]` 

- from: Enter 1~ number to specify the number of schematic file which you want to start import.(Optional)
- to: Enter 1~ number of last to import.(Optional)

Example: `/worldimport 1 3` ... this will import only 3 schematic files.

This command import schematic files which you exported last time.


## Important point!

**These two commands takes very long time.**
You should check `{Minecraft install directory}/logs/debug.log` by `less +F` command while exporting and importing.

After `/worldimport`, Minecraft App will be halt, but please don't close it!
Forge is running and generates `{World directory}/region/*.mca` files.

Importing is really finished when *.mca files are not updated.
Check /region/ directory to judge finished or not.

I also recommend to try with small area first.
Importing larger area takes 10 ~ hours...
With my PC (Ryzen7 3700X, 32 GM memory), 3000 x 130 x 3000 takes 12 hours.