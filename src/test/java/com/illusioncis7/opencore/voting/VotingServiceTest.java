import com.illusioncis7.opencore.config.ConfigService;
import com.illusioncis7.opencore.database.Database;
import com.illusioncis7.opencore.gpt.GptService;
import com.illusioncis7.opencore.reputation.ReputationService;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.function.Consumer;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VotingServiceTest {

    @Mock
    private JavaPlugin plugin;
    @Mock
    private Database database;
    @Mock
    private GptService gptService;
    @Mock
    private ConfigService configService;
    @Mock
    private ReputationService reputationService;

    private VotingService service;

    @BeforeEach
    public void setup() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        service = new VotingService(plugin, database, gptService, configService, reputationService);
    }

    @Test
    public void testInvalidParameterStoredAsError() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement psSelect = mock(PreparedStatement.class);
        PreparedStatement psError = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(database.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(ArgumentMatchers.contains("config_params"))).thenReturn(psSelect);
        when(conn.prepareStatement(ArgumentMatchers.contains("gpt_reasoning"))).thenReturn(psError);
        when(psSelect.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getBoolean(1)).thenReturn(false); // not editable

        doAnswer(inv -> {
            Consumer<String> cb = inv.getArgument(2);
            cb.accept("{\"id\":1,\"value\":\"new\"}");
            return null;
        }).when(gptService).submitRequest(anyString(), any(), any());

        Method m = VotingService.class.getDeclaredMethod("mapConfigChange", int.class, UUID.class, String.class);
        m.setAccessible(true);
        m.invoke(service, 5, UUID.randomUUID(), "change setting");

        verify(conn, never()).prepareStatement(ArgumentMatchers.contains("parameter_id"));
        verify(conn).prepareStatement(ArgumentMatchers.contains("gpt_reasoning"));
        verify(psError).executeUpdate();
    }
}
