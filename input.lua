local function fib(n)
if n<=1 then
return n
else
return fib(n-1)+fib(n-2)
end
end

local t={}
for i=1,10 do
t[i]=fib(i)
end

for i,v in ipairs(t) do
print(("%d: %d"):format(i,v))
end
