local function factorial(n)

    if n <= 1 then
        return 1
    else
        return n * factorial(n - 1)
    end
end
local t = {1, 2, 3, 4, 5}
for i, v in ipairs(t) do
    print(i, v)
end
local x = 1
local y = 2
local z = x + y
if x > 0 then
    print("positive")
elseif x == 0 then
    print("zero")
else
    print("negative")
end
repeat
    print("looping")
until x > 10
local tbl = {name = "test", value = 42}
print(tbl.name)
