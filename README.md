Java项目实践--新哥点评
---
### 1. 新哥点评项目介绍
- #### 项目介绍：
    - 本项目（新哥点评）是一个以社交平台为核心的轻电商项目。其主要围绕着 Redis 各种技术展开，并且项目中也包含了许多电商的业务场景，如短信登录、商户查询缓存、优惠劵秒杀、附近的商户、UV统计、用户签到、好友关注、达人探店等。可以说，此项目把 Redis 中各种各样的数据结构、各种各样的应用姿势“玩”了个遍。
- #### 项目内容：
    -  共享session登录问题
        - 这部分会使用Redis来处理Session不共享问题。
        - 但其实我在之前的瑞吉外卖的项目优化部分就做过了，用Redis替换session来存储邮箱验证码。
    - 商户查询缓存
        - 这部分要理解缓存击穿，缓存穿透，缓存雪崩等问题，对于这些概念的理解不仅仅是停留在概念上，更是能在代码中看到对应的内容。
    - 优惠劵秒杀
        - 这部分我们可以学会Redis的计数器功能，结合Lua脚本完成高性能的Redis操作，同时学会Redis分布式锁的原理，包括Redis的三种消息队列。
    - 达人探店
        - 基于List来完成点赞列表的操作，同时基于SortedSet来完成点赞的排行榜功能。
    - 好友关注
        - 基于Set集合的关注、取消关注，共同关注等等功能。
    - 附近商户
        - 利用Redis的GEOHash结构来完成对于地理坐标的操作。
    - 用户签到
        - 使用Redis的BitMap数据实现签到以及统计功能。
    - UV统计
        - 主要是使用Redis的HyperLogLog结构来完成统计功能。
- #### 项目技术：
    - 后端
        - SpringMVC框架、SpringBoot框架
        - Mysql数据库
        - Mybatis Plus工具
        - Maven工具
        - Redis缓存
        - Lua脚本
    - 前端
        - Nginx服务器
        - HTML、CSS、JS技术
        - VUE框架
        - Element-UI框架
    - 调试、测试
        - Postman
        - JMeter 

### 2.新哥点评项目过程
- #### 共享session登录问题
    - 当我们发送验证码时，以手机号为key，存储验证码（String）
    - 登录验证通过后，以随机token为key，存储用户数据（Hash）
    - 每次发送请求时，需要进行身份校验，并且还需要刷新token有效期，这些步骤适合放在拦截器中处理
        - ##### 发送验证码
            ```
            @Override
            public Result sendCode(String phone, HttpSession session) {
                // 1.校验手机号
                if (RegexUtils.isPhoneInvalid(phone)){
                    // 2.如果不符合，返回错误消息
                    return Result.fail("手机号码格式错误！");
                }
                // 3.符合，生成验证码
                String code = RandomUtil.randomNumbers(6);

                // 4.保存验证码到 redis
                stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

                // 5.发送验证码
                log.debug("发送短信验证码成功，验证码：{}",code);
                // 返回ok
                return Result.ok();
            }
            ```
        - ##### 登录验证
            ```
            @Override
            public Result login(LoginFormDTO loginForm, HttpSession session) {
                // 1.校验手机号
                String phone = loginForm.getPhone();
                if (RegexUtils.isPhoneInvalid(phone)){
                    // 2.如果不符合，返回错误消息
                    return Result.fail("手机号码格式错误！");
                }
                // 3.从redis获取验证码并校验
                String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
                String code = loginForm.getCode();
                if (cacheCode == null || !cacheCode.equals(code)){
                    // 3.1.不一致，报错
                    return Result.fail("验证码错误");
                }

                // 4.一致，根据手机号查询用户
                User user = query().eq("phone", phone).one();

                // 5.判断用户是否存在
                if (user == null){
                    // 6.不存在，创建新用户并保存
                    user = createUserWithPhone(phone);
                }

                // 7.保存用户信息到 redis 中
                // 7.1.随机生成token，作为登录令牌
                String token = UUID.randomUUID().toString(true);
                // 7.2.将User对象转为HashMap存储
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                        CopyOptions.create()
                                .setIgnoreNullValue(true)
                                .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
                // 7.3.存储
                String tokenKey = LOGIN_USER_KEY + token;
                stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
                // 7.4.设置token有效期
                stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);

                // 8.返回token
                return Result.ok(token);
            }
            ```
        - ##### 刷新token有效期的拦截器
            ```
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                // 1.获取请求头中的token
                String token = request.getHeader("authorization");
                if (StrUtil.isBlank(token)){
                    return true;
                }
                // 2.基于token获取redis中的用户
                String key = RedisConstants.LOGIN_USER_KEY + token;
                Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
                // 3.判断用户是否存在
                if (userMap.isEmpty()){
                    return true;
                }
                // 4.将查询到的Hash数据转为UserDTO对象
                UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
                // 5.存在，保存用户信息到ThreadLocal，方便在后面service中拿出使用
                UserHolder.saveUser(userDTO);
                // 6.刷新token有效期
                stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL,TimeUnit.MINUTES);
                // 7.放行
                return true;
            }
            ```
        - ##### 登录拦截器
            ```
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                // 判断是否需要拦截（ThreadLocal中是否有用户）
                if (UserHolder.getUser() == null){
                    // 没有，需要拦截，设置状态码
                    response.setStatus(401);
                    // 拦截
                    return false;
                }
                // 有用户，则放行
                return true;
            }
            ```

- #### 商户查询缓存
    - 使用缓存空对象来解决缓存穿透问题
    - 使用给不同Key的TTL添加随机值来解决缓存雪崩问题
    - 分别使用逻辑锁和逻辑过期来解决缓存击穿问题
        - ##### 缓存空对象解决缓存穿透
            ```
            public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time, TimeUnit unit){
                String key = keyPrefix + id;
                // 1.从redis查询商铺缓存
                String json = stringRedisTemplate.opsForValue().get(key);
                // 2.判断是否存在
                if (StrUtil.isNotBlank(json)){
                    // 3.存在，直接返回
                    return JSONUtil.toBean(json,type);
                }
                // 判断命中的是否是空值
                if (json != null){
                    // 返回一个错误信息
                    return null;
                }

                // 4.不存在，根据id查询数据库
                R r = dbFallback.apply(id);
                // 5.不存在，返回错误
                if (r == null){
                    // 将空值写入redis
                    stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                    // 返回错误信息
                    return null;
                }
                // 6.存在，写入redis
                this.set(key,r,time,unit);
                // 7.返回
                return r;
            }
            ```
        - ##### 互斥锁解决缓存击穿
            ```
            public Shop queryWithMutex(Long id){
                String key = CACHE_SHOP_KEY + id;
                // 1.从redis查询商铺缓存
                String shopJson = stringRedisTemplate.opsForValue().get(key);
                // 2.判断是否存在
                if (StrUtil.isNotBlank(shopJson)){
                    // 3.存在，直接返回
                    return JSONUtil.toBean(shopJson, Shop.class);
                }
                // 判断命中的是否是空值
                if (shopJson != null){
                    // 返回一个错误信息
                    return null;
                }

                // 4.实现缓存重建
                // 4.1.获取互斥锁
                String lockKey = "lock:shop:" + id;
                Shop shop = null;
                try {
                    boolean isLock = tryLock(lockKey);
                    // 4.2.判断是否获取成功
                    if (!isLock){
                        // 4.3.失败，则休眠并重试
                        Thread.sleep(50);
                        return queryWithMutex(id);
                    }

                    // 4.4.成功，根据id查询数据库
                    shop = getById(id);
                    // 模拟重建延时
                    Thread.sleep(200);
                    // 5.不存在，返回错误
                    if (shop == null){
                        // 将空值写入redis
                        stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                        // 返回错误信息
                        return null;
                    }
                    // 6.存在，写入redis
                    stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }finally {
                    // 7.释放互斥锁
                    unlock(lockKey);
                }
                // 8.返回
                return shop;
            }
            ```
        - ##### 逻辑过期解决缓存击穿
            ```
            private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

            public <R,ID> R queryWithLogicalExpire(
                    String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time, TimeUnit unit){
                String key = keyPrefix + id;
                // 1.从redis查询商铺缓存
                String json = stringRedisTemplate.opsForValue().get(key);
                // 2.判断是否存在
                if (StrUtil.isBlank(json)){
                    // 3.未命中，直接返回
                    return null;
                }
                // 4.命中，需先把json反序列化为对象
                RedisData redisData = JSONUtil.toBean(json, RedisData.class);
                R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
                LocalDateTime expireTime = redisData.getExpireTime();
                // 5.判断是否过期
                if (expireTime.isAfter((LocalDateTime.now()))) {
                    // 5.1.未过期，直接返回店铺信息
                    return r;
                }
                // 5.2.已过期，需要缓存重建
                // 6.缓存重建
                // 6.1.获取互斥锁
                String lockKey = LOCK_SHOP_KEY + id;
                boolean isLock = tryLock(lockKey);
                // 6.2.判断是否获取锁成功
                if (isLock){
                    // 6.3.成功，开启独立线程，实现缓存重建
                    CACHE_REBUILD_EXECUTOR.submit(() ->{
                        try {
                            // 查询数据库
                            R r1 = dbFallback.apply(id);
                            // 写入redis
                            this.setWithLogicalExpire(key,r1,time,unit);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }finally {
                            // 释放锁
                            unlock(lockKey);
                        }
                    });
                }
                // 6.4.返回过期的店铺信息
                return r;
            }
            ```

- #### 优惠券秒杀
    - 借助redis的incr指令并且拼接一些其他信息实现全局唯一ID
    - 使用乐观锁的CAS方法的改良法解决库存超卖问题
    - 使用悲观锁实现一人一单功能
        - ##### 全局ID生成器
            ```
            @Component
            public class RedisIdWorker {
                /**
                * 开始时间戳，这里设定的是2022.01.01 00:00:00
                */
                private static final long BEGIN_TIMESTAMP = 1640995200L;

                /**
                * 序列号的位数
                */
                private static final int COUNT_BITS = 32;

                private StringRedisTemplate stringRedisTemplate;

                public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
                    this.stringRedisTemplate = stringRedisTemplate;
                }

                public long nextId(String keyPrefix){
                    // 1.生成时间戳
                    LocalDateTime now = LocalDateTime.now();
                    long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
                    long timestamp = nowSecond - BEGIN_TIMESTAMP;

                    // 2.生成序列号
                    // 2.1.获取当前日期，精确到天
                    String data = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
                    // 2.2.自增长
                    long count = stringRedisTemplate.opsForValue().increment("irc:" + keyPrefix + ":" + data);

                    // 3.拼接并返回
                    return timestamp << COUNT_BITS | count;
                }
            }
            ```
        - ##### 解决一人一单和超卖问题
            ```
            @Transactional
            public void createVoucherOrder(VoucherOrder voucherOrder) {
                // 5.一人一单（悲观锁）
                Long userId = voucherOrder.getUserId();

                // 5.1.查询订单
                int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
                // 5.2.判断是否存在
                if (count > 0) {
                    // 用户已经购买过了
                    log.error("用户已经购买过一次！");
                    return;
                }

                // 6.扣减库存(乐观锁-CAS方案)
                boolean success = seckillVoucherService.update()
                        .setSql("stock = stock - 1") // set stock = stock - 1
                        .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
                        .update();
                if (!success) {
                    // 扣减失败
                    log.error("库存不足！");
                    return;
                }

                // 7.创建订单
                save(voucherOrder);
            }
            ```
    
- #### Redis分布式锁/Redisson
    - 使用redis的SETNX方法实现分布式锁
    - 使用线程标识解决分布式锁误删问题
    - 采用Lua脚本实现释放锁功能解决多条命令原子性问题
        - ##### 实现分布式锁并解决误删问题
            ```
            private static final String KEY_PREFIX = "lock:";
            private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

            @Override
            public boolean tryLock(long timeoutSec) {
                // 获取线程标识
                String threadId = ID_PREFIX + Thread.currentThread().getId();

                // 获取锁，使用SETNX方法进行加锁，同时设置过期时间，防止死锁
                Boolean success = stringRedisTemplate.opsForValue()
                        .setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
                //自动拆箱可能会出现null，这样写更稳妥
                return Boolean.TRUE.equals(success);
            }
            ```
        - ##### Lua脚本解决多条命令原子性问题
            ```
            private static final String KEY_PREFIX = "lock:";
            private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
            private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
            static {
                UNLOCK_SCRIPT = new DefaultRedisScript<>();
                UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
                UNLOCK_SCRIPT.setResultType(Long.class);
            }

            @Override
            public void unlock() {
                // 调用lua脚本
                stringRedisTemplate.execute(
                        UNLOCK_SCRIPT,
                        Collections.singletonList(KEY_PREFIX + name),
                        ID_PREFIX + Thread.currentThread().getId());
            }
            ```
            ```
            -- unlock.lua-释放锁Lua脚本
            -- 比较线程标识与锁中的标识是否一致
            if(redis.call('get',KEYS[1]) == ARGV[1]) then
                -- 释放锁 del key
                return redis.call('del',KEYS[1])
            end
            return 0
            ```

- #### 秒杀优化
    - 新增秒杀优惠券的同时，将优惠券的信息保存到Redis中，并采用Lua脚本完成秒杀资格判断
    - 基于阻塞队列完成独立线程异步下单功能
    - 基于Redis的Stream数据结构实现消息队列进一步完善线程异步下单模块
        - ##### Redis优化秒杀资格判断
            ```
            @Transactional
            public void addSeckillVoucher(Voucher voucher) {
                // 保存优惠券
                save(voucher);
                // 保存秒杀信息
                SeckillVoucher seckillVoucher = new SeckillVoucher();
                seckillVoucher.setVoucherId(voucher.getId());
                seckillVoucher.setStock(voucher.getStock());
                seckillVoucher.setBeginTime(voucher.getBeginTime());
                seckillVoucher.setEndTime(voucher.getEndTime());
                seckillVoucherService.save(seckillVoucher);
                // 保存秒杀库存到Redis中
                stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(),voucher.getStock().toString());
            }
            ```
            ```
            -- seckill.lua-秒杀优化Lua脚本
            -- 1.参数列表
            -- 1.1.优惠券id
            local voucherId = ARGV[1]
            -- 1.2.用户id
            local userId = ARGV[2]
            -- 1.3.订单id
            local orderId = ARGV[3]

            -- 2.数据key
            -- 2.1.库存key
            local stockKey = 'seckill:stock:' .. voucherId
            -- 2.2.订单key
            local orderKey = 'seckill:order:' .. voucherId

            -- 3.脚本业务
            -- 3.1.判断库存是否充足
            if (tonumber(redis.call('get',stockKey)) <= 0 ) then
                -- 3.2.库存不足，返回1
                return 1
            end
            -- 3.3.判断用户是否下单 SISMEMBER orderKey userId
            if (redis.call('sismember',orderKey,userId) == 1) then
                -- 3.4.存在，说明是重复下单，返回2
                return 2
            end
            -- 3.5.扣库存 incrby stockKey -1
            redis.call('incrby',stockKey,-1)
            -- 3.6.下单（保存用户） sadd orderKey userId
            redis.call('sadd',orderKey,userId)
            -- 3.7.发送消息到队列中， XADD stream.orders * k1 v1 k2 v2
            redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)
            return 0
            ```
        - ##### 阻塞队列实现秒杀优化
            ```
            // 创建阻塞队列
            private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
            // 创建线程任务
            private class VoucherOrderHandler implements Runnable{

                @Override
                public void run() {
                    while (true){
                        try {
                            // 1.获取队列中的订单信息
                            VoucherOrder voucherOrder = orderTasks.take();
                            // 2.创建订单
                            handleVoucherOrder(voucherOrder);
                        } catch (Exception e) {
                            log.error("处理订单异常",e);
                        }
                    }
                }
            }
            ```
            ```
            private void handleVoucherOrder(VoucherOrder voucherOrder) {
                // 1.获取用户
                Long userId = voucherOrder.getUserId();
                // 2.创建锁对象
                RLock lock = redissonClient.getLock("lock:order:" + userId);
                // 3.获取锁
                boolean isLock = lock.tryLock();
                // 4.判断是否获取锁成功
                if (!isLock) {
                    // 获取锁失败，返回错误或重试
                    log.error("不允许重复下单");
                    return;
                }
                try {
                    proxy.createVoucherOrder(voucherOrder);
                } finally {
                    // 释放锁
                    lock.unlock();
                }
            }
            ```
        - ##### Stream消息队列完善异步秒杀下单
            ```
            private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
            static {
                SECKILL_SCRIPT = new DefaultRedisScript<>();
                SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
                SECKILL_SCRIPT.setResultType(Long.class);
            }

            // 创建线程池
            private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

            // 类初始化完成后执行这个方法
            @PostConstruct
            private void init(){
                SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
            }

            // 创建线程任务
            private class VoucherOrderHandler implements Runnable{
                String queueName = "stream.orders";
                @Override
                public void run() {
                    while (true){
                        try {
                            // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                            List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                                    Consumer.from("g1", "c1"),
                                    StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                                    StreamOffset.create(queueName, ReadOffset.lastConsumed())
                            );
                            // 2.判断消息获取是否成功
                            if (list == null || list.isEmpty()){
                                // 2.1.如果获取失败，说明没有消息，继续下一次循环
                                continue;
                            }
                            // 3.解析消息中的订单信息
                            MapRecord<String, Object, Object> record = list.get(0);
                            Map<Object, Object> values = record.getValue();
                            VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                            // 4.如果获取成功，可以下单
                            handleVoucherOrder(voucherOrder);
                            // 5.ACK确认 SACK stream.order g1 id
                            stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                        } catch (Exception e) {
                            log.error("处理订单异常",e);
                            handlePendingList();
                        }
                    }
                }

                private void handlePendingList() {
                    while (true){
                        try {
                            // 1.获取pending-list中的订单信息 XREADG ROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                            List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                                    Consumer.from("g1", "c1"),
                                    StreamReadOptions.empty().count(1),
                                    StreamOffset.create(queueName, ReadOffset.from("0"))
                            );
                            // 2.判断消息获取是否成功
                            if (list == null || list.isEmpty()){
                                // 2.1.如果获取失败，说明pending-list没有异常消息，结束循环
                                break;
                            }
                            // 3.解析消息中的订单信息
                            MapRecord<String, Object, Object> record = list.get(0);
                            Map<Object, Object> values = record.getValue();
                            VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                            // 4.如果获取成功，可以下单
                            handleVoucherOrder(voucherOrder);
                            // 5.ACK确认 SACK stream.order g1 id
                            stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                        } catch (Exception e) {
                            log.error("处理pending-list订单异常",e);
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                }
            }
            ```
            ```
            private IVoucherOrderService proxy;

            @Override
            public Result seckillVoucher(Long voucherId) {
                // 获取用户
                Long userId = UserHolder.getUser().getId();
                // 获取订单id
                long orderId = redisIdWorker.nextId("order");
                // 1.执行lua脚本
                Long result = stringRedisTemplate.execute(
                        SECKILL_SCRIPT,
                        Collections.emptyList(),
                        voucherId.toString(), userId.toString(),String.valueOf(orderId)
                );
                // 2.判断结果是为0
                int r = result.intValue();
                if(r != 0){
                    // 2.1.不为0，代表没有购买资格
                    return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
                }

                // 3.获取代理对象（事务想要生效，还得利用代理来生效）
                proxy = (IVoucherOrderService) AopContext.currentProxy();

                // 4.返回订单id
                return Result.ok(orderId);
            }
            ```

- #### 达人探店
    - 编写文件上传代码实现发布探店笔记功能
    - 利用Redis中的set集合来完成笔记点赞功能
    - 使用SortedSet(ZSet)完善笔记点赞和点赞排行榜功能
        - ##### 上传文件
            ```
            @PostMapping("blog")
            public Result uploadImage(@RequestParam("file") MultipartFile image) {
                try {
                    // 获取原始文件名称
                    String originalFilename = image.getOriginalFilename();
                    // 生成新文件名
                    String fileName = createNewFileName(originalFilename);
                    // 保存文件
                    image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
                    // 返回结果
                    log.debug("文件上传成功，{}", fileName);
                    return Result.ok(fileName);
                } catch (IOException e) {
                    throw new RuntimeException("文件上传失败", e);
                }
            }
            ```
            ```
            private String createNewFileName(String originalFilename) {
                // 获取后缀
                String suffix = StrUtil.subAfter(originalFilename, ".", true);
                // 生成目录
                String name = UUID.randomUUID().toString();
                int hash = name.hashCode();
                int d1 = hash & 0xF;
                int d2 = (hash >> 4) & 0xF;
                // 判断目录是否存在
                File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                // 生成文件名
                return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
            }
            ```
        - ##### 点赞功能
            ```
            @Override
            public Result likeBlog(Long id) {
                // 1.获取登录用户
                Long userId = UserHolder.getUser().getId();
                // 2.判断当前登录用户是否已经点赞
                String key = BLOG_LIKED_KEY + id;
                Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
                if (score == null){
                    // 3.如果未点赞，可以点赞
                    // 3.1.数据库点赞数 + 1
                    boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
                    // 3.2.保存用户到Redis的ZSet集合 zadd key value score
                    if (isSuccess){
                        stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
                    }
                }else {
                    // 4.如果已点赞，取消点赞
                    // 4.1.数据库点赞数 - 1
                    boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
                    // 4.2.把用户从Redis的ZSet集合移除
                    if (isSuccess){
                        stringRedisTemplate.opsForZSet().remove(key,userId.toString());
                    }
                }
                return Result.ok();
            }
            ```
        - ##### 点赞排行榜
            ```
            @Override
            public Result queryBlogLikes(Long id) {
                String key = BLOG_LIKED_KEY + id;
                // 1.查询top5的点赞用户 zrange key 0 4
                Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
                if (top5 == null || top5.isEmpty()) {
                    return Result.ok(Collections.emptyList());
                }
                // 2.解析出其中的用户id
                List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
                String idStr = StrUtil.join(",", ids);
                // 3.根据用户id查询用户 WHERE id IN (5,1) ORDER BY FIELD(id,5,1)
                List<UserDTO> userDTOS = userService.query()
                        .in("id",ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                        .stream()
                        .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                        .collect(Collectors.toList());
                // 4.返回
                return Result.ok(userDTOS);
            }
            ```

- #### 好友关注
    - 利用Redis中的set集合实现关注和取关以及共同关注功能
    - 采用SortedSet(ZSet)结构并结合推模式实现发布博客功能
    - 根据SortedSet可以按照score值排序完成滚动分页实现拉取博客的功能
        - ##### 关注取关和共同关注
            ```
            @Override
            public Result follow(Long followUserId, Boolean isFollow) {
                // 获取登录用户
                Long userId = UserHolder.getUser().getId();
                String key = "follows:" + userId;
                // 1.判断到底是关注还是取关
                if (isFollow){
                    // 2.关注，新增数据
                    Follow follow = new Follow();
                    follow.setUserId(userId);
                    follow.setFollowUserId(followUserId);
                    boolean isSuccess = save(follow);
                    if (isSuccess){
                        // 把关注用户的id，放入redis的set集合 sadd userId followUserId
                        stringRedisTemplate.opsForSet().add(key,followUserId.toString());
                    }
                }else {
                    // 3.取关，删除 delete from tb_follow where user_id = ? and follow_user_id = ?
                    boolean isSuccess = remove(new QueryWrapper<Follow>()
                            .eq("user_id", userId).eq("follow_user_id", followUserId));
                    if (isSuccess){
                        // 把关注用户的id从Redis集合中移除
                        stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
                    }
                }
                return Result.ok();
            }
            ```
            ```
            @Override
            public Result followCommons(Long id) {
                // 1.获取当前用户
                Long userId = UserHolder.getUser().getId();
                String key = "follows:" + userId;
                // 2.求交集
                String key2 = "follows:" + id;
                Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
                if (intersect == null || intersect.isEmpty()){
                    // 无交集
                    return Result.ok(Collections.emptyList());
                }
                // 3.解析id集合
                List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
                // 4.查询用户
                List<UserDTO> users = userService.listByIds(ids)
                        .stream()
                        .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                        .collect(Collectors.toList());
                return Result.ok(users);
            }
            ```
        - ##### 发布博客
            ```
            @Override
            public Result saveBlog(Blog blog) {
                // 1.获取登录用户
                UserDTO user = UserHolder.getUser();
                blog.setUserId(user.getId());
                // 2.保存探店笔记
                boolean isSuccess = save(blog);
                if (!isSuccess){
                    return Result.fail("新增笔记失败！");
                }
                // 3.查询笔记作者的所有粉丝 select * from tb_follow where follow_user_id = ?
                List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
                // 4.推送笔记id给所有粉丝
                for (Follow follow : follows){
                    // 4.1.获取粉丝id
                    Long userId = follow.getUserId();
                    // 4.2.推送
                    String key = FEED_KEY + userId;
                    stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
                }
                // 5.返回id
                return Result.ok(blog.getId());
            }
            ```
        - ##### 拉取博客
            ```
            @Override
            public Result queryBlogOfFollow(Long max, Integer offset) {
                // 1.获取当前用户
                Long userId = UserHolder.getUser().getId();
                // 2.查询收件箱 ZREVRANGEBYSCORE key Max Min LIMIT offset count
                String key = FEED_KEY + userId;
                Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                        .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
                // 3.非空判断
                if (typedTuples == null || typedTuples.isEmpty()){
                    return Result.ok();
                }
                // 4.解析数据：blogId、minTime（时间戳）、offset
                ArrayList<Object> ids = new ArrayList<>(typedTuples.size());
                long minTime = 0;
                int os = 1;
                for (ZSetOperations.TypedTuple<String> tuple : typedTuples){
                    // 4.1.获取id
                    ids.add(Long.valueOf(tuple.getValue()));
                    // 4.2.获取分数（）时间戳
                    long time = tuple.getScore().longValue();
                    if (time == minTime){
                        os++;
                    }else {
                        minTime = time;
                        os = 1;
                    }
                }
                // 5.根据id查询blog
                String idStr = StrUtil.join(",",ids);
                List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
                for(Blog blog : blogs){
                    // 5.1.查询blog有关的用户
                    queryBlogUser(blog);
                    // 5.2.查询blog是否被点赞
                    isBlogLiked(blog);
                }
                // 6.封装并返回
                ScrollResult r = new ScrollResult();
                r.setList(blogs);
                r.setOffset(os);
                r.setMinTime(minTime);
                return Result.ok(r);
            }
            ```

- #### 附近商户
    - 使用Redis中的GEO数据结构实现附近商户功能
        - ##### 导入店铺数据到GEO
            ```
            @Test
            void loadShopData(){
                // 1.查询店铺信息
                List<Shop> list = shopService.list();
                // 2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
                Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
                // 3.分批完成写入Redis
                for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
                    // 3.1.获取类型id
                    Long typeId = entry.getKey();
                    String key = SHOP_GEO_KEY + typeId;
                    // 3.2.获取同类型的店铺的集合
                    List<Shop> value = entry.getValue();
                    List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
                    // 3.3.写入redis GEOADD key 经度 纬度 member
                    for (Shop shop : value) {
                        // stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
                        locations.add(new RedisGeoCommands.GeoLocation<>(
                                shop.getId().toString(),
                                new Point(shop.getX(),shop.getY())
                        ));
                    }
                    stringRedisTemplate.opsForGeo().add(key,locations);
                }
            }
            ```
        - ##### 实现附近商户功能
            ```
            @Override
            public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
                // 1.判断是否需要根据坐标查询
                if(x == null || y == null){
                    // 不需要坐标查询，按数据库查询
                    Page<Shop> page =query()
                            .eq("type_id",typeId)
                            .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
                    // 返回数据
                    return Result.ok(page.getRecords());
                }

                // 2.计算分页参数
                int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
                int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

                // 3.查询redis、按照距离排序、分页。结果：shopId、distance
                String key = SHOP_GEO_KEY +typeId;
                GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                        .search(
                                key,
                                GeoReference.fromCoordinate(x, y),
                                new Distance(5000),
                                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeCoordinates().limit(end)
                        );

                // 4.解析出id
                if(results == null){
                    return Result.ok(Collections.emptyList());
                }
                List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
                if (list.size() <= from){
                    // 没有下一页
                    return Result.ok(Collections.emptyList());
                }
                // 4.1.截取 from ~ end 的部分
                List<Long> ids = new ArrayList<>(list.size());
                Map<String,Distance> distanceMap = new HashMap<>(list.size());
                list.stream().skip(from).forEach(
                    result ->{
                        // 4.2.获取店铺id
                        String shopIdStr = result.getContent().getName();
                        ids.add(Long.valueOf(shopIdStr));
                        // 4.3.获取距离
                        Distance distance = result.getDistance();
                        distanceMap.put(shopIdStr,distance);
                });

                // 5.根据id查询Shop
                String idStr = StrUtil.join(",", ids);
                List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
                for (Shop shop : shops) {
                    shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
                }

                // 6.返回
                return Result.ok(shops);
            }
            ```

- #### 用户签到
    - 使用Redis中的BitMap数据结构实现用户签到和签到统计功能
        - ##### 签到功能
            ```
            @Override
            public Result sign() {
                // 1.获取当前登录用户
                Long userId = UserHolder.getUser().getId();
                // 2.获取日期
                LocalDateTime now = LocalDateTime.now();
                // 3.拼接key
                String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
                String key = USER_SIGN_KEY + userId + keySuffix;
                // 4.获取今天是本月的第几天
                int dayOfMonth = now.getDayOfMonth();
                // 5.写入Redis SETBIT key offset 1
                stringRedisTemplate.opsForValue().setBit(key,dayOfMonth - 1,true);
                return Result.ok();
            }
            ```
        - ##### 签到统计
            ```
            @Override
            public Result signCount() {
                // 1.获取当前登录用户
                Long userId = UserHolder.getUser().getId();
                // 2.获取日期
                LocalDateTime now = LocalDateTime.now();
                // 3.拼接key
                String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
                String key = USER_SIGN_KEY + userId + keySuffix;
                // 4.获取今天是本月的第几天
                int dayOfMonth = now.getDayOfMonth();
                // 5.获取本月截至今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
                List<Long> result = stringRedisTemplate.opsForValue().bitField(
                        key,
                        BitFieldSubCommands.create()
                                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
                );
                if (result == null || result.isEmpty()){
                    // 没有任何签到结果
                    return Result.ok(0);
                }
                Long num = result.get(0);
                if (num == null || num == 0){
                    return Result.ok(0);
                }
                // 6.循环遍历
                int count = 0;
                while(true) {
                    // 让这个数字与1做与运算，得到数字的最后一个bit位
                    // 判断这个bit位是否为0
                    if ((num & 1) == 0){
                        // 如果为0，说明未签到，结束
                        break;
                    }else{
                        // 如果不为0，说明已签到，计数器+1
                        count++;
                    }
                    // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
                    num >>>= 1;
                }
                return Result.ok(count);
            }
            ```

- #### UV统计
    - 使用Redis中的HyperLogLog数据结构实现海量数据统计的功能
        - ##### 测试百万数据的统计
            ```
            @Test
            void testHyperLogLog(){
                String[] values = new String[1000];
                int j = 0;
                for (int i = 0; i < 1000000; i++) {
                    j = i % 1000;
                    values[j] = "user_" + i;
                    if (j == 999){
                        // 发送到Redis
                        stringRedisTemplate.opsForHyperLogLog().add("hl2",values);
                    }
                }
                // 统计数量
                Long count = stringRedisTemplate.opsForHyperLogLog().size("hl2");
                System.out.println("count = " + count);
            }
            ```

- #### 工具类
    - ##### 自定义常量工具类(优雅)
        ```
        public class RedisConstants {
            public static final String LOGIN_CODE_KEY = "login:code:";
            public static final Long LOGIN_CODE_TTL = 2L;
            public static final String LOGIN_USER_KEY = "login:token:";
            public static final Long LOGIN_USER_TTL = 30L;

            public static final Long CACHE_NULL_TTL = 2L;

            public static final Long CACHE_SHOP_TTL = 30L;
            public static final String CACHE_SHOP_KEY = "cache:shop:";

            public static final String LOCK_SHOP_KEY = "lock:shop:";
            public static final Long LOCK_SHOP_TTL = 10L;

            public static final String SECKILL_STOCK_KEY = "seckill:stock:";
            public static final String BLOG_LIKED_KEY = "blog:liked:";
            public static final String FEED_KEY = "feed:";
            public static final String SHOP_GEO_KEY = "shop:geo:";
            public static final String USER_SIGN_KEY = "sign:";
        }
        ```
        ```
        public abstract class RegexPatterns {
            /**
            * 手机号正则
            */
            public static final String PHONE_REGEX = "^1([38][0-9]|4[579]|5[0-3,5-9]|6[6]|7[0135678]|9[89])\\d{8}$";
            /**
            * 邮箱正则
            */
            public static final String EMAIL_REGEX = "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$";
            /**
            * 密码正则。4~32位的字母、数字、下划线
            */
            public static final String PASSWORD_REGEX = "^\\w{4,32}$";
            /**
            * 验证码正则, 6位数字或字母
            */
            public static final String VERIFY_CODE_REGEX = "^[a-zA-Z\\d]{6}$";
        }
        ```
        ```
        public class SystemConstants {
            public static final String IMAGE_UPLOAD_DIR = "E:\\git Repositories\\Hm-Dianping\\nginx-1.18.0\\html\\hmdp\\imgs\\";
            public static final String USER_NICK_NAME_PREFIX = "user_";
            public static final int DEFAULT_PAGE_SIZE = 5;
            public static final int MAX_PAGE_SIZE = 10;
        }
        ```
    - ##### 校验格式工具类
        ```
        public class RegexUtils {
            /**
            * 是否是无效手机格式
            * @param phone 要校验的手机号
            * @return true:符合，false：不符合
            */
            public static boolean isPhoneInvalid(String phone){
                return mismatch(phone, RegexPatterns.PHONE_REGEX);
            }
            /**
            * 是否是无效邮箱格式
            * @param email 要校验的邮箱
            * @return true:符合，false：不符合
            */
            public static boolean isEmailInvalid(String email){
                return mismatch(email, RegexPatterns.EMAIL_REGEX);
            }

            /**
            * 是否是无效验证码格式
            * @param code 要校验的验证码
            * @return true:符合，false：不符合
            */
            public static boolean isCodeInvalid(String code){
                return mismatch(code, RegexPatterns.VERIFY_CODE_REGEX);
            }

            // 校验是否不符合正则格式
            private static boolean mismatch(String str, String regex){
                if (StrUtil.isBlank(str)) {
                    return true;
                }
                return !str.matches(regex);
            }
        }
        ```
    - ##### 保存登录用户信息工具类
        ```
        public class UserHolder {
            private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

            public static void saveUser(UserDTO user){
                tl.set(user);
            }

            public static UserDTO getUser(){
                return tl.get();
            }

            public static void removeUser(){
                tl.remove();
            }
        }
        ```
- ***此项目过程详细知识可以去看我的Redis总结：[REDIS](https://github.com/JackyST0/Java-Technology-Stack/blob/master/Redis.md)***

### 3.新哥点评项目总结
- 我觉得这个项目挺适合有了一定Java后端开发经验且正在学习Redis技术的同学们练手的。首先其使用了前后端分离的模式，前端使用了Nginx做代理服务器、后端则使用了SpringBoot框架来开发，这些技术在当下都是非常流行的。其次项目也包含了许多与电商平台相关的业务场景，可供我们整合Redis技术针对性的解决并优化每个场景下可能会出现的问题，从而使我们对真实的开发环境有进一步的了解。因此，此项目绝对是用来巩固并提升自己所学前后端知识的不二之选。

- ##### 反正不管，跟着新哥学就对了！！！
---
参考视频：[新哥点评](https://www.bilibili.com/video/BV1cr4y1671t?p=24&vd_source=a6541efd5c43d30c410c9e45054c9b89)