# G-BuildTools

**NOTE: Get the latest version from the G-ExtensionStore in G-Earth 1.5.1+, and not from the /releases in github**

**NOTE 2: Report bugs [here](https://github.com/sirjonasxx/G-BuildTools/issues) and check this page regularly for updates**

G-BuildTools is an advanced G-Earth extension that attempts to compensate all Habbo's shortcomings when it comes to building experience. It currently contains 8 features

* If you have an instable connection or experience furni not being moved, it is recommended to adjust your ratelimiter in _Settings_: ![image](https://user-images.githubusercontent.com/36828922/125685672-5ff87d7d-2fee-4928-827b-aa336ae4514b.png)
* Do not forget to **enable** the extension before using it

Youtube demo's:
1. [Furni Mover - Demo](https://www.youtube.com/watch?v=zdP5-REGP-M)
2. [Poster Mover - Demo](https://www.youtube.com/watch?v=7lGu5yvtpXI)
3. [Quickdrop - Demo](https://www.youtube.com/watch?v=Z7YXwfDyMVA)
4. [Wired Duplicator - Demo](https://www.youtube.com/watch?v=SgQZwKdnBkY)
5. [Stacktile Tools - Demo](https://www.youtube.com/watch?v=-5gHMBWeBQo)
6. [Invisible Furni Tools - Demo](https://www.youtube.com/watch?v=A8TEgt4mKXc)
7. Hide furni - coming soon
8. Black hole stacking - coming soon

## Furni mover
![image](https://user-images.githubusercontent.com/36828922/144688474-c8d3aaff-aedd-483a-8f2d-2cf3c8df97ae.png)

**3 modes are available:**
* Tile _-> Move all the furniture on a single tile when you type ```:move```_
* Auto _-> The same as "tile", but stays enabled until you type ```:abort```_
* Rectangle _-> Move all furniture in a region_
   * You may select _Inverse direction_, to mirror the selection

By default, "Use stacktile" is enabled to copy the exact heights of every furniture. You may also unselect it
There are **3 options:**
* Match height
* Offset _-> move the selection with an offset_
* Flatten _-> Place all the furniture at the same height, useful for hiding wired_

**Commands:**
* :move
* :abort _-> aborts remaining movements_
* :undo _-> you may undo any past movements_
* :exclude _-> exclude a type of furniture from being moved (for example, the floor). You will be asked to shift+click a furniture of the type_
* :include _-> undo the :exclude_
* :reset _-> clear the list of excluded furni types_

**Other options:**
* The checkbox **Visual help** gives you information inside your client
* **Wired safety** makes sure to move wired in an order so a stack is never left without it's conditions _(warning: in the current implementation, this changes the order of the wired boxes)_


## Poster mover
![image](https://user-images.githubusercontent.com/36828922/125196834-a0a4fa80-e25b-11eb-9cd6-9abb4edccd45.png)

Move a poster manually to let G-BuildTools detect which poster you want to move. Afterwards, you can use all options in the Poster Mover to move the poster, you can also edit the "location code" directly (press enter to save)


## General
![image](https://user-images.githubusercontent.com/36828922/125197481-19a55180-e25e-11eb-905d-0fbe2165a611.png)


### Quickdrop furniture
![image](https://user-images.githubusercontent.com/36828922/125194069-f2e01e80-e24f-11eb-9e15-793db389ddd3.png)

* Places furniture instantly, replacing them with a black loading box until the furniture is put
* If you placed a furniture in an invalid location, you may need to reload inventory to get it back (settings -> reload inventory)

### Wired duplicator
![image](https://user-images.githubusercontent.com/36828922/125194155-64b86800-e250-11eb-9b74-5fd9fd471968.png)

1. Set up a wired box
2. Select "Last condition/effect/trigger" depending on the type of wired
3. Double click the wireds you want to update to the same settings as the wired box in step 1

### Stacktile tools
![image](https://user-images.githubusercontent.com/36828922/125196911-e5309600-e25b-11eb-9a2e-452b40e7b0c8.png)

Self explanatory


### Invisible furni tools
![image](https://user-images.githubusercontent.com/36828922/125196925-f11c5800-e25b-11eb-9d61-aa43f1babd43.png)

Self explanatory

## Extra
![image](https://user-images.githubusercontent.com/36828922/144688513-f74a38a0-9356-499d-b035-52cbce773406.png)

Self explanatory


