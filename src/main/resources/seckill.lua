local voucherId = ARGV[1]
local userId = ARGV[2]

-- 获取库存和订单key，分别用于判断是否有库存，以及该订单是否已经由某个用户购买过
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 判断库存是否充足
if (tonumber(redis.call('get', stockKey)) <= 0) then
     -- 库存不足
     return 1
end

-- 判断该用户是否已经买过该优惠券
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 已经买过
    return 2
end

-- 扣库存
redis.call('incrby', stockKey, -1)
-- 下单（保存用户）
redis.call('sadd', orderKey, userId)

return 0