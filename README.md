# G-BuildTools
G-BuildTools is an advanced G-Earth extension that attempts to compensate all Habbo's shortcomings when it comes to building experience. It currently contains 6 features

* If you have an instable connection, it is recommended to set your ratelimit lower in _Settings_: ![image](https://user-images.githubusercontent.com/36828922/125194599-46ec0280-e252-11eb-8b70-bcdc541b8e25.png)
* Do not forget to **enable** the extension before using it

Youtube demo's:
TODO

## Furni mover
![image](https://user-images.githubusercontent.com/36828922/125194835-4b64eb00-e253-11eb-8a41-01790e85666a.png)

**3 modes are available:**
* Tile _-> Move all the furniture on a single tile when you type ```:move```_
* Auto _-> The same as "tile", but stays enabled until you type ```:abort```_
* Rectangle _-> Move all furniture in a region_
   * You may select _Inverse direction_, to mirror the selection

By default, "Use stacktile (2x2)" is enabled to copy the exact heights of every furniture. You may also unselect it
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
* **Wired safety** makes sure to move wired in an order so a stack is never left without it's conditions


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


