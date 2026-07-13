-- Luau-specific features

export type Point = {x:number, y:number}
local function createPoint(x:number, y:number):Point

    return {x = x, y = y}
end
type Vector3 = {x:number, y:number, z:number}
local function magnitude(v:Vector3):number

    return math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
end
local function process(items:{number}):{number}

    local result:{number} = {}
    for _, item in ipairs(items) do
        if item > 0 then
            table.insert(result, item)
        end
    end
    return result
end
continue
local function test()

    for i = 1, 10 do
        if i == 5 then
            continue
        end
        print(i)
    end
end
