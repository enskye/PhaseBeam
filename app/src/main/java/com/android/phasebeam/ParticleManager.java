package com.android.phasebeam;

import java.util.Random;

public class ParticleManager
{
    public static class Particle
    {
        float x;
        float y;
        float z;

        Particle(float[] data)
        {
            x = data[0];
            y = data[1];
            z = data[2];
        }

        public float[] toFloatArray()
        {
            return new float[]{x, y, z};
        }
    }

    Random random = new Random();

    //region Particle data
        private final int particleCount = 26;
        private final int particlePropertyCount = 3;
        private final int particleArrayLength = particleCount * particlePropertyCount;
        private final int particleArrayDataSize = particleArrayLength * 4;
        private final float[] particleData = new float[particleArrayLength];
        private final float[] beamData = new float[particleArrayLength];
    //endregion

    //region Dimensional data
        public volatile float xOffset = 0.5f;
        private float oldXOffset = 0.5f;
        private float newXOffset = 0.5f;
        public float backgroundXOffset = 0.0f;
        public float particleXOffset = 0.0f;
    //endregion

    ParticleManager()
    {
        initializeParticles();
    }

    public int getParticleArrayDataSize()
    {
        return this.particleArrayDataSize;
    }

    public float[] getParticleData()
    {
        return particleData;
    }

    public float[] getBeamData()
    {
        return beamData;
    }

    public int getParticleCount()
    {
        return particleCount;
    }

    public void setXOffset(float xOffset)
    {
        this.xOffset = xOffset;
    }

    private void initializeParticles()
    {
        for (int i = 0; i < particleCount; i++)
        {
            int index = i * particlePropertyCount;

            Particle particle = new Particle(new float[particlePropertyCount]);

            particle.x = boundRandom(-1.25f, 1.25f);
            particle.y = boundRandom(-1.25f, 1.25f);

            float z;

            if (i < 3)
            {
                z = 14.0f;
            }
            else if (i < 4)
            {
                z = boundRandom(10.0f, 20.0f);
            }
            else if (i < 7)
            {
                z = 25.0f;
            }
            else if (i == 10)
            {
                z = 24.0f;
                particle.x = 1.0f;
            }
            else
            {
                z = boundRandom(6.0f, 14.0f);
            }

            particle.z = z;


            float[] newParticle = particle.toFloatArray();
            System.arraycopy(newParticle, 0, particleData, index, particlePropertyCount);
        }

        for(int i = 0; i < particleCount; i++)
        {
            int index = i * particlePropertyCount;

            Particle beam = new Particle(new float[particlePropertyCount]);

            float z;

            if(i < 20)
            {
                z = boundRandom(4.0f, 10.0f) / 2.0f;
            }
            else
            {
                z = boundRandom(4.0f, 35.0f) / 2.0f;
            }

            beam.x = boundRandom(-1.25f, 1.25f);
            beam.y = boundRandom(-1.05f, 1.205f);

            beam.z = z;

            float[] newBeam = beam.toFloatArray();
            System.arraycopy(newBeam, 0, beamData, index, particlePropertyCount);
        }
    }

    public void updateParticles(long deltaTime)
    {
        float deltaTimeFactor = deltaTime / 66.0f; // This adjusts it to the designed 15fps or so
        boolean offsetSettled = newXOffset == oldXOffset;

        for (int i = 0; i < particleCount; i++)
        {
            int x = i * particlePropertyCount;
            int y = x + 1;
            int z = x + 2;

            if (offsetSettled)
            {
                if (beamData[x] / beamData[z] > 0.5f)
                {
                    beamData[x] = -1.0f;
                }

                if (particleData[x] / particleData[z] > 0.5f)
                {
                    particleData[x] = -1.0f;
                }

                if (beamData[y] > 1.05f)
                {
                    beamData[y] = -1.05f;
                    beamData[x] = boundRandom(-1.25f, 1.25f);
                }
                else
                {
                    beamData[y] += 0.000160f * deltaTimeFactor * beamData[z];
                }

                if (particleData[y] > 1.25f)
                {
                    particleData[y] = -1.25f;
                    particleData[x] = boundRandom(-1.25f, 1.25f);
                }
                else
                {
                    particleData[y] += 0.00022f * deltaTimeFactor * particleData[z];
                }
            }

            beamData[x] += 0.0001f * deltaTimeFactor * beamData[z];

            // the next beams z value because the renderscript can use pointer magic but we can't
            float nextBeamZ = beamData[((i + 1) % particleCount) * particlePropertyCount + 2];

            particleData[x] += 0.0001560f * deltaTimeFactor * nextBeamZ;
        }

        particleXOffset = newXOffset;
    }

    public void tickXOffset()
    {
        oldXOffset = newXOffset;
        newXOffset = xOffset * 2;

        if (newXOffset != oldXOffset)
        {
            backgroundXOffset = -xOffset / 2.0f;
        }
    }

    private float boundRandom(float min, float max)
    {
        return min + random.nextFloat() * (max - min);
    }
}
