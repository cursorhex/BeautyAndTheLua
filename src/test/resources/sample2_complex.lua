#!/usr/bin/env lua
--[[
Complex test file with various Lua/Luau features
]]

local function process(data, opts)

    local result = {}
    for i = 1, #data do
        local item = data[i]
        if item.active then
            if item.priority > 10 then
                table.insert(result, {id = item.id, status = "high"})
            elseif item.priority > 5 then
                table.insert(result, {id = item.id, status = "medium"})
            else
                table.insert(result, {id = item.id, status = "low"})
            end
        end
    end
    return result
end
local meta = {__add =
function(a, b) return {x = a.x + b.x, y = a.y + b.y} end
, __tostring =
function(self) return string.format("(%d,%d)", self.x, self.y) end
}
local Vector = {}
Vector.__index = Vector
function Vector.new(x, y)

    return setmetatable({x = x, y = y}, Vector)
end
local v1 = Vector.new(1, 2)
local v2 = Vector.new(3, 4)
print(v1 + v2)
