package com.example.leaseplatform.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

/**
 * 把 {@code Long} 类型的<b>主键/外键字段</b>序列化为 JSON <b>字符串</b>，避免前端精度截断。
 *
 * <h3>问题</h3>
 * 本项目主键是 MyBatis-Plus 雪花 ID（约 {@code 2.1e18}），而 JavaScript 的
 * {@code Number.MAX_SAFE_INTEGER} 只有 {@code 9007199254740991}（约 {@code 9.0e15}）——
 * 雪花 ID 超出它约 <b>233 倍</b>。若按 JSON 数字下发，前端 {@code JSON.parse} 会静默取整：
 * <pre>
 *   数据库真实值 : 2105649876774645761
 *   前端拿到的值 : 2105649876774645760   （差 -2）
 * </pre>
 * 更严重的是：{@code 2.1e18} 附近 float64 的最小间隔（ULP）是 <b>256</b>，
 * 因此<b>相差不到 256 的两个 ID 会被前端看成同一个数</b>。
 * 雪花 ID 在同一毫秒内按序号递增（差 1），于是「同一毫秒创建的连续记录」
 * 在列表里会塌成同一个 ID —— 表现为前端列表顺序错乱、按 ID 匹配/去重/跳详情全部错位。
 * 这正是「数据顺序有点问题」的根因。
 *
 * <h3>方案</h3>
 * 只对<b>字段名是 {@code id} 或以 {@code Id} 结尾</b>且类型为 {@code Long/long} 的属性
 * 改用 {@link ToStringSerializer}，下发为字符串。
 * <ul>
 *   <li>不做「全局 Long → 字符串」：那会把金额（分）、时间戳（epoch millis）、
 *       {@code PageResult.total} 一并变成字符串，误伤面过大；</li>
 *   <li>不需要在 43 个 DTO 的 154 个字段上逐个加 {@code @JsonSerialize} 注解 ——
 *       用 Jackson 的 {@link ValueSerializerModifier} 统一处理，新增 DTO 自动生效；</li>
 *   <li>字段名判定用 {@code endsWith("Id")}（大写 I）：{@code openid} 这类小写结尾的
 *       字符串字段不会被误判。</li>
 * </ul>
 *
 * <h3>兼容性</h3>
 * <ul>
 *   <li><b>请求体不受影响</b>：Jackson 默认允许把字符串强制转成 Long，
 *       前端即使回传 {@code "productId": "123"} 也能正常绑定；</li>
 *   <li><b>响应体是破坏性变更</b>：ID 由数字变字符串，前端若用 {@code ===} 与数字比较
 *       需要改为与字符串比较（但原先用数字比较本来就是错的，见上）；</li>
 *   <li>该 {@code ObjectMapper} 同时被 {@code MultiLevelCache} 使用，
 *       因此 Redis 里的缓存 JSON 与 HTTP 下发的表示形式保持一致。</li>
 * </ul>
 */
@Configuration
public class SnowflakeIdJacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer snowflakeIdAsStringCustomizer() {
        return builder -> builder.addModule(idAsStringModule());
    }

    private static SimpleModule idAsStringModule() {
        SimpleModule module = new SimpleModule("snowflake-id-as-string");
        module.setSerializerModifier(new ValueSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                            BeanDescription.Supplier beanDesc,
                                                            List<BeanPropertyWriter> beanProperties) {
                for (BeanPropertyWriter property : beanProperties) {
                    if (isIdProperty(property.getName())
                            && isLongType(property.getType())
                            && !property.hasSerializer()) {
                        property.assignSerializer(ToStringSerializer.instance);
                    }
                }
                return beanProperties;
            }
        });
        return module;
    }

    /** {@code id} 或以大写 {@code Id} 结尾（如 {@code userId} / {@code categoryId}） */
    private static boolean isIdProperty(String name) {
        return "id".equals(name) || name.endsWith("Id");
    }

    private static boolean isLongType(JavaType type) {
        if (type == null) {
            return false;
        }
        Class<?> raw = type.getRawClass();
        return raw == Long.class || raw == long.class;
    }
}
