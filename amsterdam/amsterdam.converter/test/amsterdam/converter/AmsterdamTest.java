package amsterdam.converter;

import org.junit.Test;

public class AmsterdamTest {

	@Test
	public void testMain() throws Exception {
		Amsterdam.main(new String[] {"docs/negatives.csv", "out.csv"});
	}
}
