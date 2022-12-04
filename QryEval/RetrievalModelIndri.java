/**
 *  An object that stores parameters for the Indri
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {

        // parameters in Model BM25
        private double mu;
        private double lambda;

        /**
         * Constructor
         * @param mu
         * @param lambda
         */
        public RetrievalModelIndri(double mu, double lambda) {
                this.mu = mu;
                this.lambda = lambda;
        }

        /**
         * Getter for mu.
         * @return mu
         */
        public double getMu() {
                return mu;
        }

        /**
         * Getter for lambda.
         * @return lambda
         */
        public double getLambda() {
                return lambda;
        }

        /**
         * The name of the default query operator for the retrieval model.
         * @return AND query operator is the default query operator
         */
        @Override
        public String defaultQrySopName() {
                return new String ("#and");
        }
}
