import buildcraft.additionalpipes.APConfiguration;
import buildcraft.additionalpipes.api.*;
import buildcraft.additionalpipes.chunkloader.*;
import buildcraft.additionalpipes.filter.SideFilterData;
import buildcraft.additionalpipes.network.*;
import buildcraft.additionalpipes.pipes.TeleportManager;
import buildcraft.additionalpipes.service.*;
import buildcraft.additionalpipes.utils.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Compile/run against Java 17 with no Minecraft libraries: service regression checks. */
public final class InfrastructureRegression {
    private static int checks;
    private static void require(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static void rejects(Runnable run, String why) {
        checks++;
        try { run.run(); throw new AssertionError("Expected failure: " + why); }
        catch (IllegalArgumentException expected) { }
    }
    private static final PipeLocation A = new PipeLocation("minecraft:overworld", 0, 64, 0);

    private static final class Pipe implements ITeleportPipe {
        int frequency; byte state; boolean publicly; boolean valid=true;
        UUID owner; String name="owner"; PipeType type; PipeLocation location;
        Pipe(PipeType medium, UUID owner, boolean publicly, int frequency, byte state, PipeLocation loc) {
            this.type=medium; this.owner=owner; this.publicly=publicly;
            this.frequency=frequency; this.state=state; this.location=loc;
        }
        public void setFrequency(int value) { frequency=value; }
        public int getFrequency() { return frequency; }
        public byte getState() { return state; }
        public void setState(byte value) { state=value; }
        public boolean isPublic() { return publicly; }
        public void setPublic(boolean value) { publicly=value; }
        public PipeType getType() { return type; }
        public PipeLocation getPosition() { return location; }
        public UUID getOwnerUUID() { return owner; }
        public String getOwnerName() { return name; }
        public boolean isValid() { return valid; }
    }

    private static void teleport() {
        TeleportManager manager=new TeleportManager();
        UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        Pipe src=new Pipe(PipeType.ITEMS, a, true, 3, (byte) 3, A);
        Pipe recv=new Pipe(PipeType.ITEMS, b, true, 3, (byte) 2, A);
        Pipe send=new Pipe(PipeType.ITEMS, b, true, 3, (byte) 1, A);
        Pipe privateA=new Pipe(PipeType.ITEMS, a, false, 3, (byte) 2, A);
        Pipe otherFreq=new Pipe(PipeType.ITEMS, b, true, 9, (byte) 2, A);
        Pipe fluid=new Pipe(PipeType.FLUIDS, b, true, 3, (byte) 2, A);
        for(Pipe p:List.of(src,recv,send,privateA,otherFreq,fluid)) manager.add(p,p.getFrequency());
        manager.add(recv,recv.getFrequency());
        require(manager.getPipes(PipeType.ITEMS).size()==5,"Duplicate add must not create extra endpoint");
        require(manager.getConnectedPipes(src,false,true).equals(List.of(recv)),"public receive routing");
        require(manager.getConnectedPipes(src,true,false).equals(List.of(send)),"send routing");
        src.publicly=false;
        require(manager.getConnectedPipes(src,false,true).equals(List.of(privateA)),"owner-only routing");
        require(TeleportPermissions.mayConnect(src,privateA),"same owner may connect");
        require(!TeleportPermissions.mayConnect(src,recv),"private routes must not leak to public");
        require(!TeleportPermissions.mayEdit(privateA,b,false),"private edit denied");
        require(TeleportPermissions.mayEdit(privateA,a,false),"private owner allowed");
        require(TeleportPermissions.mayEdit(privateA,b,true),"op privilege allowed");
        privateA.valid=false;
        require(manager.getConnectedPipes(src,false,true).isEmpty(),"invalid endpoints excluded");
        privateA.valid=true;
        recv.setFrequency(5);
        manager.add(recv,5);
        src.publicly=true;
        require(manager.getConnectedPipes(src,true,true).equals(List.of(send)),"Moved endpoint removed from old channel");
        manager.remove(recv,3);
        require(manager.getPipes(PipeType.ITEMS).size()==4,"Remove ignores stale caller frequency");
        require(APCommandService.listTeleportEndpoints(manager,"power",20).isEmpty(),"power does not list fluid pipes");
        manager.setFrequencyName(3,"Transport");
        require(manager.getFrequencyName(3).equals("Transport"),"frequency label store");
        rejects(()-> manager.setFrequencyName(-1,"x"),"negative frequency label");
        manager.reset();
        require(manager.getPipes(PipeType.ITEMS).isEmpty() && manager.getFrequencyName(3).isEmpty(),"reset leaks no data");
    }

    private static void configAndFrequencyStore() throws Exception {
        Path tmp=Files.createTempDirectory("ap-config-test-");
        try {
            Files.writeString(tmp.resolve(APConfiguration.FILE_NAME),"powerTransmittance=120\nchunkSightRange=900\nlaserKeyChar=garbage\ngravityFeedPipeTicksPerPull=-5\nfilterRightclicks=true\nunknownSetting=kept\n");
            APConfiguration p=APConfiguration.load(tmp);
            require(p.powerTransmittanceCfg==1.0,"Power percentage must clamp to 100%");
            require(p.chunkSightRange==31,"Viewer range must clamp");
            require(p.laserKeyCode==68,"Invalid int fallback");
            require(p.gravityFeedPipeTicksPerPull==1,"Pull interval >= 1");
            require(p.filterRightclicks,"Booleans parsed");
            require(Files.readString(tmp.resolve(APConfiguration.FILE_NAME)).contains("unknownSetting=kept"),"Keep unknown config values");
            TeleportManager manager=new TeleportManager();
            manager.setFrequencyName(7,"Public BCCE");
            TeleportFrequencyStore.save(tmp,manager);
            manager.reset();
            TeleportFrequencyStore.load(tmp,manager);
            require(manager.getFrequencyName(7).equals("Public BCCE"),"Frequency names persist per save");
            Files.writeString(tmp.resolve("data/additionalpipes-frequencies.properties"),"frequency.xxx=malformed\nfrequency.12=Valid\nfrequency.-10=No\n");
            manager.reset();
            TeleportFrequencyStore.load(tmp,manager);
            require(manager.getFrequencyName(12).equals("Valid") && manager.getFrequencyName(7).isEmpty(),"Ignore corrupt frequency data");
        } finally { try(var files=Files.walk(tmp)) { files.sorted(Comparator.reverseOrder()).forEach(f->{try {Files.deleteIfExists(f);}catch(IOException e){throw new RuntimeException(e);}}); } }
    }

    private static void packets() throws Exception {
        var options=new SideFilterData.Options(true,false,true);
        var location=new PipeLocation("minecraft:the_nether", -32, 100, 4096);
        List<APPacket> messages=List.of(
            new APPacket.ValueUpdate(APMessageType.DISTRIBUTION_UPDATE,location,(byte)3,29),
            new APPacket.TeleportUpdate(location,55,true,(byte)3),
            new APPacket.AdvancedWoodUpdate(location),
            new APPacket.TeleportData(location,new int[]{5,6,7},UUID.randomUUID(),"Player"),
            new APPacket.ChunkloadRequest(),
            new APPacket.ChunkloadData("minecraft:overworld",List.of(new ChunkCoordinates(-2,7),new ChunkCoordinates(2,9))),
            new APPacket.ValueUpdate(APMessageType.PRIORITY_UPDATE,location,(byte)5,3),
            new APPacket.JeweledServerUpdate(location,(byte)6,options),
            new APPacket.JeweledClientUpdate(location,List.of(options,options,options,options,options,options))
        );
        for (APPacket packet:messages) {
            byte[] encoded=APProtocol.encode(packet);
            APPacket decoded=APProtocol.decode(encoded,packet.type().serverbound());
            require(decoded.type()==packet.type(),"Packet discriminator roundtrip: "+packet.type());
            require(Arrays.equals(encoded,APProtocol.encode(decoded)),"Packet data roundtrip: "+packet.type());
            try { APProtocol.decode(encoded,!packet.type().serverbound()); throw new AssertionError("direction accepted "+packet.type()); }
            catch (IOException expected) { checks++; }
            byte[] withJunk=Arrays.copyOf(encoded,encoded.length+1);
            try { APProtocol.decode(withJunk,packet.type().serverbound()); throw new AssertionError("trailing byte accepted"); }
            catch (IOException expected) { checks++; }
        }
        try { APProtocol.decode(new byte[]{(byte)255,0},true); throw new AssertionError("Invalid version accepted"); }
        catch (IOException expected) { checks++; }
        try { APProtocol.decode(new byte[APProtocol.MAX_PACKET_BYTES+1],true); throw new AssertionError("oversized packet accepted"); }
        catch (IOException expected) { checks++; }
        var server=new PacketHandler();
        AtomicInteger applied=new AtomicInteger();
        UUID allowed=UUID.randomUUID(), denied=UUID.randomUUID();
        server.registerServerHandler(APMessageType.TELEPORT_UPDATE,
            (uuid,p)-> allowed.equals(uuid), (uuid,p)->applied.incrementAndGet());
        byte[] edit=APProtocol.encode(new APPacket.TeleportUpdate(A,20,false,(byte)1));
        server.receive(edit,true,null);
        server.receive(edit,true,denied);
        server.receive(edit,true,allowed);
        require(applied.get()==1,"Unauthorized edits must not run");
        server.clearHandlers(); server.receive(edit,true,allowed);
        require(applied.get()==1,"Missing handlers must fail closed");
    }

    private static void chunkViewer() {
        Set<ChunkCoordinates> area=ChunkLoadArea.square(-1,-1,1);
        require(area.size()==9 && area.contains(new ChunkCoordinates(-1,-1)),"Negative chunk coordinate floor division");
        require(ChunkLoadArea.square(16,0,0).equals(Set.of(new ChunkCoordinates(1,0))),"One-chunk loader");
        var visible=ChunkLoadViewDataProxy.chunksNear(0,0,2,
            List.of(new ChunkCoordinates(0,0),new ChunkCoordinates(5,0),new ChunkCoordinates(-2,1)));
        require(visible.equals(List.of(new ChunkCoordinates(-2,1),new ChunkCoordinates(0,0))),"range symmetric and sorted");
        var viewer=new ChunkLoadViewDataProxy();
        require(viewer.receive("minecraft:overworld",visible),"Initial snapshot changed");
        require(!viewer.receive("minecraft:overworld",List.of(new ChunkCoordinates(0,0),new ChunkCoordinates(-2,1))),"Same chunks no duplicate rendering");
        require(viewer.receive("minecraft:the_end",visible),"Dimension change invalidates snapshot");
        viewer.setVisible(true);
        require(viewer.shouldRequest(40),"First refresh");
        require(!viewer.shouldRequest(50),"No per-tick request flood");
        require(viewer.shouldRequest(140),"100-tick refresh");
        viewer.reset(); require(viewer.visibleChunks().isEmpty() && !viewer.visible(),"Clear when leaving world");
    }

    private static void filtering() {
        record Item(String id,int meta,String data) { }
        var matcher=new SideFilterData.ItemMatcher<Item>() {
            public boolean sameItem(Item x,Item y){return x.id.equals(y.id);}
            public boolean sameMetadata(Item x,Item y){return x.meta==y.meta;}
            public boolean sameComponents(Item x,Item y){return x.data.equals(y.data);}
        };
        var filter=new SideFilterData<Item>();
        filter.set(0,new Item("iron",1,"a"));
        require(filter.matchesStack(new Item("iron",2,"b"),matcher),"Item-only match");
        filter.setMatchMetadata(true);
        require(!filter.matchesStack(new Item("iron",2,"a"),matcher),"Metadata mismatch");
        filter.setMatchNBT(true);
        require(!filter.matchesStack(new Item("iron",1,"b"),matcher),"Components mismatch");
        require(filter.matchesStack(new Item("iron",1,"a"),matcher),"All exact fields");
        require(filter.snapshot().size()==27 && filter.snapshot().get(1)==null,"Filter snapshot includes empty slots");
        filter.clear(); require(!filter.matchesStack(new Item("iron",1,"a"),matcher),"Clear filter");
        rejects(()->filter.set(27,new Item("X",0,"")),"Invalid slot");

        class FakeSlots implements InventoryUtils.Slots<Item> {
            final Item[] slots={new Item("iron",0,""),null,null};
            public int size(){return 3;}
            public Item stackInSlot(int i){return slots[i];}
            public int count(Item p){return p.meta;}
            public int slotLimit(int slot,Item p){return slot==2?16:64;}
            public int maximumStackSize(Item p){return 64;}
            public boolean canInsert(int slot,Item p){return slot!=1;}
            public boolean canMerge(Item a,Item b){return a.id.equals(b.id);}
        }
        var slots=new FakeSlots(); slots.slots[0]=new Item("iron",63,"");
        require(InventoryUtils.canItemFit(slots,new Item("iron",17,"")),"partial stack plus empty valid slot");
        require(!InventoryUtils.canItemFit(slots,new Item("iron",18,"")),"Whole stack must fit, not just one item");
        require(InventoryUtils.getFirstFreeSlot(slots,new Item("iron",1,""))==2,"First insertable empty slot");
        require(InventoryUtils.containsItem(slots,new Item("iron",0,""),false,false,matcher),"Inventory matching");
    }

    private static void bitmap() throws IOException {
        for (int size : new int[]{0,1,7,8,9,16,31,32}) {
            boolean[] flags=new boolean[size];
            for(int i=0;i<size;i++) flags[i]=(i%3==0);
            var bytes=new ByteArrayOutputStream();
            NetworkUtils.writeBooleanArray(new DataOutputStream(bytes),flags);
            require(bytes.size()==(size+7)/8,"Bitmap packing has no padding byte");
            boolean[] decoded=NetworkUtils.readBooleanArray(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),size);
            require(Arrays.equals(flags,decoded),"Bitmap roundtrip");
        }
    }

    private static void pipeState() {
        var state=new TeleportPipeState(22,(byte)3,true,UUID.randomUUID(),"Tester");
        require(state.equals(TeleportPipeState.fromLegacyFields(state.toLegacyFields())),"Classic pipe state persistence");
        var malformed=TeleportPipeState.fromLegacyFields(Map.of("freq","nonsense","ownerUUID","broken","state","255"));
        require(malformed.frequency()==0 && malformed.state()==3 && malformed.ownerUUID()==null,"Malformed legacy NBT fields sanitized");
        rejects(()->new TeleportPipeState(-1,(byte)0,false,null,""),"Negative frequency");
    }

    public static void main(String[] args) throws Exception {
        teleport(); configAndFrequencyStore(); packets(); chunkViewer(); filtering(); bitmap(); pipeState();
        System.out.println("PASS Additional Pipes service regression: "+checks+" assertions");
    }
}
