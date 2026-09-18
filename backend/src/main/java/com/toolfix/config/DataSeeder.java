package com.toolfix.config;

import com.toolfix.domain.*;
import com.toolfix.repository.*;
import com.toolfix.service.HazardDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    
    private final HazardKeywordRepository hazardKeywordRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final HazardDetectionService hazardDetectionService;
    
    @Override
    public void run(String... args) {
        if (hazardKeywordRepository.count() == 0) {
            seedHazardKeywords();
        }
        
        if (knowledgeBaseRepository.count() == 0) {
            seedKnowledgeBase();
        }
        
        if (shopRepository.count() == 0) {
            seedDemoShopAndProducts();
        }
        
        // 关键修复：拦截器缓存在 @PostConstruct 时早于本播种器执行，
        // 首次启动会加载到 0 个关键词导致高危拦截静默失效，这里强制刷新。
        hazardDetectionService.refreshKeywords();
        
        log.info("Data seeding completed");
    }
    
    private void seedHazardKeywords() {
        List<HazardKeyword> keywords = Arrays.asList(
            createHazard("smoke", "⚠️ STOP IMMEDIATELY! Please disconnect the battery and stop using the tool right away. Smoke indicates a serious issue. Our technical team will contact you within 24 hours for a replacement or repair."),
            createHazard("smoking", "⚠️ STOP IMMEDIATELY! Please disconnect the battery and stop using the tool right away. Smoke indicates a serious issue. Our technical team will contact you within 24 hours for a replacement or repair."),
            createHazard("fire", "🚨 URGENT SAFETY ALERT! Please disconnect the battery immediately and move the tool to a safe, non-flammable area. Do not use it again. Our team will contact you urgently for immediate assistance."),
            createHazard("spark", "⚠️ SAFETY WARNING! Please stop using the tool and disconnect the battery. Sparking can indicate an electrical fault. Our technical team will assist you shortly."),
            createHazard("sparks", "⚠️ SAFETY WARNING! Please stop using the tool and disconnect the battery. Sparking can indicate an electrical fault. Our technical team will assist you shortly."),
            createHazard("sparking", "⚠️ SAFETY WARNING! Please stop using the tool and disconnect the battery. Sparking can indicate an electrical fault. Our technical team will assist you shortly."),
            createHazard("electric shock", "🚨 CRITICAL SAFETY ISSUE! Please do not use the tool again. Disconnect the battery if safe to do so. Electric shock is extremely dangerous. Our team will contact you immediately."),
            createHazard("shocked", "🚨 CRITICAL SAFETY ISSUE! Please do not use the tool again. Disconnect the battery if safe to do so. Electric shock is extremely dangerous. Our team will contact you immediately."),
            createHazard("electrocuted", "🚨 CRITICAL SAFETY ISSUE! Please do not use the tool again. Disconnect the battery if safe to do so. Electric shock is extremely dangerous. Our team will contact you immediately."),
            createHazard("battery swelling", "⚠️ BATTERY SAFETY ALERT! A swollen battery is dangerous. Please remove it carefully and place it in a safe, cool area away from flammable materials. Do not charge or use it. We will arrange a replacement immediately."),
            createHazard("swollen battery", "⚠️ BATTERY SAFETY ALERT! A swollen battery is dangerous. Please remove it carefully and place it in a safe, cool area away from flammable materials. Do not charge or use it. We will arrange a replacement immediately."),
            createHazard("battery bulging", "⚠️ BATTERY SAFETY ALERT! A bulging battery is dangerous. Please remove it carefully and place it in a safe, cool area away from flammable materials. Do not charge or use it. We will arrange a replacement immediately."),
            createHazard("abnormal heat", "⚠️ TEMPERATURE WARNING! Please stop using the tool immediately and let it cool down. Remove the battery. Abnormal heating can indicate a fault. Our team will help diagnose the issue."),
            createHazard("extremely hot", "⚠️ TEMPERATURE WARNING! Please stop using the tool immediately and let it cool down. Remove the battery. Abnormal heating can indicate a fault. Our team will help diagnose the issue."),
            createHazard("burning smell", "⚠️ STOP IMMEDIATELY! A burning smell indicates potential danger. Please disconnect the battery and stop using the tool. Our technical team will contact you urgently."),
            createHazard("burnt smell", "⚠️ STOP IMMEDIATELY! A burning smell indicates potential danger. Please disconnect the battery and stop using the tool. Our technical team will contact you urgently."),
            createHazard("melting", "🚨 CRITICAL ALERT! Please disconnect the battery immediately and stop using the tool. Place it in a safe location. Melting components indicate serious damage. Our team will contact you urgently.")
        );
        
        hazardKeywordRepository.saveAll(keywords);
        log.info("Seeded {} hazard keywords", keywords.size());
    }
    
    private HazardKeyword createHazard(String keyword, String response) {
        HazardKeyword hazard = new HazardKeyword();
        hazard.setKeyword(keyword);
        hazard.setLevel(HazardKeyword.HazardLevel.CRITICAL);
        hazard.setSafetyResponse(response);
        hazard.setActive(true);
        return hazard;
    }
    
    private void seedKnowledgeBase() {
        List<KnowledgeBase> knowledgeList = Arrays.asList(
            createKnowledge("Battery in Sleep Mode", 
                "Battery won't charge, LED lights don't turn on when pressing battery button, tool shows no power",
                "The battery has entered sleep/protection mode after being unused for a long period or fully discharged. This is a safety feature, not a defect.",
                "1. Remove battery from tool and charger\n2. Press battery button 3-5 times rapidly\n3. Firmly reinsert battery into charger until you hear a click\n4. If LED still doesn't light up, try a different outlet\n5. Wait 30 seconds and check if charging LED appears\n6. Once charged, test in tool",
                "battery-sleep-mode",
                "battery,charge,dead,not working,no power,led,sleep,dormant"),
            
            createKnowledge("Forward/Reverse Lock Engaged",
                "Tool won't start, trigger doesn't work, no response when pressing trigger even with charged battery",
                "The forward/reverse selector switch is in the middle (locked) position. This is a safety feature to prevent accidental startup.",
                "1. Locate the forward/reverse switch (usually above the trigger)\n2. Push the switch FULLY to the LEFT or RIGHT\n3. You should feel it click into position\n4. Try the trigger again\n5. The switch must be in either full forward or full reverse position to work",
                "forward-reverse-lock",
                "won't start,trigger,not working,no response,locked,switch"),
            
            createKnowledge("Speed Control Set Too Low",
                "Tool runs but seems very weak, barely any power, much slower than expected",
                "The variable speed control dial or trigger is set to a low setting. Many users are unaware of this feature.",
                "1. Look for a numbered dial (usually 1-10 or 1-20) on top or side of tool\n2. Turn dial to higher number for more speed\n3. Also try: squeeze trigger FULLY (many tools have variable-speed triggers)\n4. For maximum power: dial at highest number + trigger fully squeezed\n5. Test on actual work material",
                "speed-control-low",
                "weak,slow,no power,underpowered,speed,rpm"),
            
            createKnowledge("Chuck Not Tightened Properly",
                "Drill bit slips, bit falls out during use, clicking/grinding noise, bit wobbles",
                "The chuck (bit holder) is not fully tightened. Hand-tightening alone is often insufficient.",
                "1. Remove the bit completely\n2. Open chuck fully by rotating counter-clockwise\n3. Insert bit at least 3/4 inch deep into chuck\n4. Hand-tighten by rotating chuck clockwise\n5. IMPORTANT: Use the chuck key (if provided) or firmly grip chuck sleeve and turn until you hear 2-3 clicking sounds\n6. Tug the bit - it shouldn't move at all",
                "chuck-not-tight",
                "bit slips,falls out,loose,wobble,clicking,chuck"),
            
            createKnowledge("Wrong Drill Bit for Material",
                "Bit won't penetrate material, tool bogs down, produces smoke or burning smell (but tool itself is not defective)",
                "Using wrong bit type for the material, or bit is dull. This is a usage issue, not tool defect.",
                "1. Wood: Use wood/brad point bits (pointed tip)\n2. Metal: Use HSS (High Speed Steel) bits (dark/black color)\n3. Masonry/Concrete: Must use carbide-tipped masonry bits (usually labeled)\n4. Start with pilot hole (smaller bit first) for large holes\n5. If bit smokes, it's dull or wrong type - replace bit\n6. Use slower speed for harder materials",
                "wrong-drill-bit",
                "won't drill,stuck,burning,smoke from bit,bit hot,not penetrating"),
            
            createKnowledge("Battery Contacts Dirty or Corroded",
                "Intermittent power, tool cuts out randomly, battery not making good connection, tool works sometimes but not always",
                "Dirt, sawdust, or light corrosion on battery or tool contacts prevents good electrical connection.",
                "1. Remove battery from tool\n2. Inspect gold/copper contacts on both battery and tool\n3. Use clean, dry cloth to wipe all contacts\n4. For stubborn dirt: use pencil eraser gently on contacts\n5. Blow out any sawdust from battery compartment\n6. Reinsert battery firmly until it clicks\n7. Test tool",
                "dirty-contacts",
                "intermittent,cuts out,works sometimes,connection,contact,random"),
            
            createKnowledge("Torque Setting Too Low (Clutch Engaged)",
                "Tool stops/clicks when drilling, doesn't seem powerful, clicking sound when trying to drill or drive screws",
                "The torque clutch is set too low. This is designed to prevent over-tightening screws, but blocks drilling.",
                "1. Look for numbered ring behind chuck (1-20 or similar)\n2. Rotate ring to highest number for drilling\n3. Or rotate to drill bit symbol (⚒ icon) for maximum torque\n4. Lower numbers are only for delicate screw-driving\n5. For drilling always use highest setting or drill mode\n6. You should hear clicks stop and feel more power",
                "torque-clutch-low",
                "clicking,stops,weak,clutch,ratcheting,torque setting"),
            
            createKnowledge("New Tool Break-In Period",
                "New tool feels stiff, trigger is hard to pull, gears sound rough or loud (but improves with use)",
                "New tools need a break-in period. Gears and components need to seat properly through initial use.",
                "1. This is normal for new tools\n2. Use tool on light-duty tasks for first hour of use\n3. Avoid max-load applications initially\n4. Noise and stiffness will decrease after 1-2 hours of use\n5. If issue persists after several uses, then contact support\n6. Not a defect - expected for brand new tools",
                "new-tool-breakin",
                "new,stiff,tight trigger,loud,rough,noise,break in"),
            
            createKnowledge("Battery Not Fully Charged",
                "Tool has very little power, dies quickly, battery indicator shows 1-2 bars, weak performance",
                "Battery is not fully charged. Partial charge significantly reduces tool performance.",
                "1. Place battery on charger\n2. Check charger LED - should show charging status\n3. Wait until LED shows FULLY CHARGED (usually green solid light)\n4. Partial charge (even 80%) gives noticeably less power\n5. First charge of new battery may take 2-3 hours\n6. Battery should feel slightly warm when charging - this is normal",
                "battery-not-full",
                "weak,dies,low power,short runtime,battery bars,charge"),
            
            createKnowledge("Cold Weather Battery Performance",
                "Battery drains very fast, tool has less power in cold weather, battery indicator drops quickly when it's cold outside",
                "Lithium-ion batteries lose significant capacity below 32°F (0°C). This is chemical limitation, not defect.",
                "1. Store batteries at room temperature when not in use\n2. Keep spare battery warm (in pocket or truck cab)\n3. Rotate batteries - use one while keeping other warm\n4. Performance will return to normal when battery warms up\n5. Never charge battery in freezing temperatures\n6. This affects ALL lithium batteries, not specific to this brand",
                "cold-weather",
                "cold,winter,drains fast,less power,freezing,low temperature"),
            
            createKnowledge("Not Suitable for Extremely Heavy-Duty Use",
                "Tool bogs down on very thick materials, can't drill large holes in metal, struggles with 3+ inch deck screws",
                "Consumer-grade cordless tools have power limits. Your task may exceed tool specifications.",
                "1. Check tool specifications for maximum capacities\n2. Use pilot holes for large screws/bits\n3. For thick metal: use slower speed and cutting fluid\n4. Take breaks to prevent overheating\n5. For extremely heavy-duty tasks daily, commercial-grade tool may be needed\n6. This isn't a defect - it's knowing your tool's limits",
                "heavy-duty-limits",
                "bogs down,struggles,heavy duty,thick material,large holes,not powerful enough"),
            
            createKnowledge("Mode Selector on Wrong Setting",
                "Tool works but something seems off, unexpected behavior, clutch clicking when shouldn't, drill mode not working",
                "Multi-mode tools (drill/driver/hammer) have selector that must be set correctly for task.",
                "1. Locate mode selector switch (usually has icons)\n2. For drilling holes: select drill bit icon (⚒)\n3. For driving screws: select screw icon with numbers\n4. For hammer drilling (masonry models): select hammer icon\n5. Make sure selector is fully clicked into position\n6. Some tools have separate switches for mode and torque",
                "wrong-mode",
                "mode,selector,hammer,drill mode,switch,setting,function"),
            
            createKnowledge("LED Light Stays On / Auto Shutoff",
                "LED light won't turn off, battery draining when not in use, trigger pressed but light staying on",
                "Many tools have LED auto-shutoff after 10-20 seconds. If it stays on, trigger may be stuck or tool needs reset.",
                "1. Remove battery and wait 30 seconds\n2. Check trigger moves freely and isn't jammed\n3. Reinsert battery\n4. LED should turn off automatically after 10-20 seconds of inactivity\n5. If LED stays on: trigger switch may need service\n6. Don't store tool with battery inserted if LED won't shut off",
                "led-light-issue",
                "led,light stays on,won't turn off,draining,trigger stuck,auto shutoff"),
            
            createKnowledge("Belt Hook / Bit Holder Confusion",
                "Can't figure out how to attach/remove belt hook, bit holder seems broken, parts seem loose or removable",
                "Belt hooks and bit holders are intentionally removable and repositionable. Not a defect.",
                "1. Belt hook usually has screw underneath - can be removed or repositioned\n2. Bit holder clips are designed to slide in/out - this is normal\n3. Check manual for diagram of removable accessories\n4. These parts allow customization for left/right hand use\n5. Firmly reattach if loose\n6. Contact support only if screw threads stripped or clip broken",
                "belt-hook",
                "belt hook,bit holder,clip,loose,removable,accessories"),
            
            createKnowledge("New Tool Plastic Smell",
                "New tool smells like plastic or chemicals when first used, slight odor when running",
                "New tools emit off-gassing smell from manufacturing. This is normal and temporary.",
                "1. This is completely normal for new tools\n2. Use tool outdoors or well-ventilated area for first few uses\n3. Smell will dissipate after 1-2 hours of use\n4. NOT a burning smell (which would be serious)\n5. Just factory oils, plastics, and packaging residue evaporating\n6. Smell should be gone within a few uses",
                "new-tool-smell",
                "smell,plastic,chemical,odor,new,factory")
        );
        
        knowledgeBaseRepository.saveAll(knowledgeList);
        log.info("Seeded {} knowledge base entries", knowledgeList.size());
    }
    
    private KnowledgeBase createKnowledge(String name, String symptoms, String cause, 
                                         String steps, String slug, String keywords) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setScenarioName(name);
        kb.setSymptomDescription(symptoms);
        kb.setRootCause(cause);
        kb.setTroubleshootingSteps(steps);
        kb.setGuidePageSlug(slug);
        kb.setKeywords(keywords);
        kb.setType(KnowledgeBase.KnowledgeType.PLATFORM_PRESET);
        kb.setActive(true);
        return kb;
    }
    
    private void seedDemoShopAndProducts() {
        Shop shop = new Shop();
        shop.setShopifyDomain("demo-tools-shop.myshopify.com");
        shop.setShopName("Demo Tools Shop");
        shop.setAccessToken("demo_access_token_" + System.currentTimeMillis());
        shop.setPlatform("SHOPIFY");
        shop.setActive(true);
        shop.setOwnerEmail("demo@toolfix.example");
        shop = shopRepository.save(shop);
        
        // 真实产品：与演示手册一一对应
        List<Product> products = Arrays.asList(
            createProduct(shop, "FD11040711", "Impact Drill 冲击钻 FD11040711", "FD11040711", "21V", "Lithium-ion"),
            createProduct(shop, "FD11050751", "Angle Grinder 角磨机 FD11050751", "FD11050751", "21V", "Lithium-ion")
        );
        
        productRepository.saveAll(products);
        log.info("Seeded demo shop and {} products", products.size());
    }
    
    private Product createProduct(Shop shop, String sku, String name, String model, 
                                 String voltage, String batteryType) {
        Product product = new Product();
        product.setShop(shop);
        product.setSku(sku);
        product.setProductName(name);
        product.setModel(model);
        product.setBatteryVoltage(voltage);
        product.setBatteryType(batteryType);
        product.setRatedPower("800W");
        product.setRatedSpeed("0-1500 RPM");
        product.setCompatibleBatteryModels("21V Lithium-ion series");
        product.setKeyComponentCodes("Motor, Chuck/Guard, Switch");
        product.setHasManual(false);
        return product;
    }
}
